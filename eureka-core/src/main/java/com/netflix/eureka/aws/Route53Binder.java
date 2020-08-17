package com.netflix.eureka.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.*;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Route53 binder implementation. Will look for a free domain in the list of service url to bind itself to via Route53.
 */
@Singleton
public class Route53Binder implements AwsBinder {
    private static final Logger logger = LoggerFactory
            .getLogger(Route53Binder.class);
    public static final String NULL_DOMAIN = "null";

    private final EurekaServerConfig serverConfig;
    private final EurekaClientConfig clientConfig;
    private final ApplicationInfoManager applicationInfoManager;

    /**
     * the hostname to register under the Route53 CNAME
     */
    private final String registrationHostname;

    private final Timer timer;

    private final AmazonRoute53Client amazonRoute53Client;

    @Inject
    public Route53Binder(EurekaServerConfig serverConfig,
                         EurekaClientConfig clientConfig,
                         ApplicationInfoManager applicationInfoManager) {
        this(getRegistrationHostnameFromAmazonDataCenterInfo(applicationInfoManager),
                serverConfig,
                clientConfig,
                applicationInfoManager);
    }

    /**
     * @param registrationHostname the hostname to register under the Route53 CNAME
     */
    public Route53Binder(String registrationHostname, EurekaServerConfig serverConfig,
                         EurekaClientConfig clientConfig, ApplicationInfoManager applicationInfoManager) {
        this.registrationHostname = registrationHostname;
        this.serverConfig = serverConfig;
        this.clientConfig = clientConfig;
        this.applicationInfoManager = applicationInfoManager;
        this.timer = new Timer("Eureka-Route53Binder", true);
        this.amazonRoute53Client =  getAmazonRoute53Client(serverConfig);
    }

    private static String getRegistrationHostnameFromAmazonDataCenterInfo(ApplicationInfoManager applicationInfoManager) {
        InstanceInfo myInfo = applicationInfoManager.getInfo();
        AmazonInfo dataCenterInfo = (AmazonInfo) myInfo.getDataCenterInfo();

        String ip = dataCenterInfo.get(AmazonInfo.MetaDataKey.publicHostname);

        if (ip == null || ip.length() == 0) {
            return dataCenterInfo.get(AmazonInfo.MetaDataKey.localHostname);
        }

        return ip;
    }

    @Override
    @PostConstruct
    public void start() {
        try {
            doBind();
            timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            doBind();
                        } catch (Throwable e) {
                            logger.error("Could not bind to Route53", e);
                        }
                    }
                },
                serverConfig.getRoute53BindingRetryIntervalMs(),
                serverConfig.getRoute53BindingRetryIntervalMs());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void doBind() throws InterruptedException {
        List<ResourceRecordSetWithHostedZone> freeDomains = new ArrayList<>();
        List<String> domains = getDeclaredDomains();
        for(String domain : domains) {
            ResourceRecordSetWithHostedZone rrs = getResourceRecordSetWithHostedZone(domain);

            if (rrs != null) {
                if (rrs.getResourceRecordSet() == null) {
                    ResourceRecordSet resourceRecordSet = new ResourceRecordSet();
                    resourceRecordSet.setName(domain);
                    resourceRecordSet.setType(RRType.CNAME);
                    resourceRecordSet.setTTL(serverConfig.getRoute53DomainTTL());
                    freeDomains.add(new ResourceRecordSetWithHostedZone(rrs.getHostedZone(), resourceRecordSet));
                } else if (NULL_DOMAIN.equals(rrs.getResourceRecordSet().getResourceRecords().get(0).getValue())) {
                    freeDomains.add(rrs);
                }
                // already registered
                if (hasValue(rrs, registrationHostname)) {
                    return;
                }
            }
        }

        for(ResourceRecordSetWithHostedZone rrs : freeDomains) {
            if (createResourceRecordSet(rrs)) {
                logger.info("Bind {} to {}" , registrationHostname, rrs.getResourceRecordSet().getName());
                return;
            }
        }

        logger.warn("Unable to find free domain in {}", domains);

    }

    private boolean createResourceRecordSet(ResourceRecordSetWithHostedZone rrs) throws InterruptedException {
        rrs.getResourceRecordSet().setResourceRecords(Arrays.asList(new ResourceRecord(registrationHostname)));
        Change change = new Change(ChangeAction.UPSERT, rrs.getResourceRecordSet());
        if (executeChangeWithRetry(change, rrs.getHostedZone())) {
            Thread.sleep(1000);
            // check change not overwritten
            ResourceRecordSet resourceRecordSet = getResourceRecordSet(rrs.getResourceRecordSet().getName(), rrs.getHostedZone());
            if (resourceRecordSet != null) {
                return resourceRecordSet.getResourceRecords().equals(rrs.getResourceRecordSet().getResourceRecords());
            }
        }
        return false;
    }

    private List<String> toDomains(List<String> ec2Urls) {
        List<String> domains = new ArrayList<>(ec2Urls.size());
        for(String url : ec2Urls) {
            try {
                domains.add(extractDomain(url));
            } catch(MalformedURLException e) {
                logger.error("Invalid url {}", url, e);
            }
        }
        return domains;
    }

    private String getMyZone() {
        InstanceInfo info = applicationInfoManager.getInfo();
        AmazonInfo amazonInfo = info != null ? (AmazonInfo) info.getDataCenterInfo() : null;
        String zone =  amazonInfo != null ? amazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone) : null;
        if (zone == null) {
            throw new RuntimeException("Cannot extract availabilityZone");
        }
        return zone;
    }

    private List<String> getDeclaredDomains() {
        final String myZone = getMyZone();
        List<String> ec2Urls = clientConfig.getEurekaServerServiceUrls(myZone);
        return toDomains(ec2Urls);
    }

    private boolean executeChangeWithRetry(Change change, HostedZone hostedZone) throws InterruptedException {
        Throwable firstError = null;
        for (int i = 0; i < serverConfig.getRoute53BindRebindRetries(); i++) {
            try {
                executeChange(change, hostedZone);
                return true;
            } catch (Throwable e) {
                if (firstError == null) {
                    firstError = e;
                }
                Thread.sleep(1000);
            }
        }

        if (firstError != null) {
            logger.error("Cannot execute change {} {}", change, firstError, firstError);
        }

        return false;
    }
    private void executeChange(Change change, HostedZone hostedZone) {
            logger.info("Execute change {} ", change);
            ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest = new ChangeResourceRecordSetsRequest();
            changeResourceRecordSetsRequest.setHostedZoneId(hostedZone.getId());
            ChangeBatch changeBatch = new ChangeBatch();

            changeBatch.withChanges(change);
            changeResourceRecordSetsRequest.setChangeBatch(changeBatch);

            amazonRoute53Client.changeResourceRecordSets(changeResourceRecordSetsRequest);
    }

    private ResourceRecordSetWithHostedZone getResourceRecordSetWithHostedZone(String domain) {
        HostedZone hostedZone = getHostedZone(domain);
        if (hostedZone != null) {
            return new ResourceRecordSetWithHostedZone(hostedZone, getResourceRecordSet(domain, hostedZone));
        }
        return null;
    }

    private ResourceRecordSet getResourceRecordSet(String domain, HostedZone hostedZone) {
        ListResourceRecordSetsRequest request = new ListResourceRecordSetsRequest();
        request.setMaxItems(String.valueOf(Integer.MAX_VALUE));
        request.setHostedZoneId(hostedZone.getId());

        ListResourceRecordSetsResult listResourceRecordSetsResult = amazonRoute53Client.listResourceRecordSets(request);

        for(ResourceRecordSet rrs : listResourceRecordSetsResult.getResourceRecordSets()) {
            if (rrs.getName().equals(domain)) {
                return rrs;
            }
        }

        return null;
    }

    private HostedZone getHostedZone(String domain) {
        ListHostedZonesRequest listHostedZoneRequest = new ListHostedZonesRequest();
        listHostedZoneRequest.setMaxItems(String.valueOf(Integer.MAX_VALUE));
        ListHostedZonesResult listHostedZonesResult = amazonRoute53Client.listHostedZones(listHostedZoneRequest);
        for(HostedZone hostedZone : listHostedZonesResult.getHostedZones()) {
            if (domain.endsWith(hostedZone.getName())) {
                return hostedZone;
            }
        }
        return null;
    }

    private void unbindFromDomain(String domain) throws InterruptedException {
        ResourceRecordSetWithHostedZone resourceRecordSetWithHostedZone = getResourceRecordSetWithHostedZone(domain);
        if (hasValue(resourceRecordSetWithHostedZone, registrationHostname)) {
            resourceRecordSetWithHostedZone.getResourceRecordSet().getResourceRecords().get(0).setValue(NULL_DOMAIN);
            executeChangeWithRetry(new Change(ChangeAction.UPSERT, resourceRecordSetWithHostedZone.getResourceRecordSet()), resourceRecordSetWithHostedZone.getHostedZone());
        }
    }

    private String extractDomain(String url) throws MalformedURLException {
        return new URL(url).getHost() + ".";
    }

    @Override
    @PreDestroy
    public void shutdown() {
        timer.cancel();

        for(String domain : getDeclaredDomains()) {
            try {
                unbindFromDomain(domain);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        amazonRoute53Client.shutdown();
    }

    private AmazonRoute53Client getAmazonRoute53Client(EurekaServerConfig serverConfig) {
        String aWSAccessId = serverConfig.getAWSAccessId();
        String aWSSecretKey = serverConfig.getAWSSecretKey();
        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withConnectionTimeout(serverConfig.getASGQueryTimeoutMs());

        if (null != aWSAccessId && !"".equals(aWSAccessId)
                && null != aWSSecretKey && !"".equals(aWSSecretKey)) {
            return new AmazonRoute53Client(
                    new BasicAWSCredentials(aWSAccessId, aWSSecretKey),
                    clientConfiguration);
        } else {
            return new AmazonRoute53Client(
                    new InstanceProfileCredentialsProvider(),
                    clientConfiguration);
        }
    }

    private boolean hasValue(ResourceRecordSetWithHostedZone resourceRecordSetWithHostedZone, String ip) {
        if (resourceRecordSetWithHostedZone != null && resourceRecordSetWithHostedZone.getResourceRecordSet() != null) {
            for (ResourceRecord rr : resourceRecordSetWithHostedZone.getResourceRecordSet().getResourceRecords()) {
                if (ip.equals(rr.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private class ResourceRecordSetWithHostedZone {
        private final HostedZone hostedZone;
        private final ResourceRecordSet resourceRecordSet;

        public ResourceRecordSetWithHostedZone(HostedZone hostedZone, ResourceRecordSet resourceRecordSet) {
            this.hostedZone = hostedZone;
            this.resourceRecordSet = resourceRecordSet;
        }

        public HostedZone getHostedZone() {
            return hostedZone;
        }

        public ResourceRecordSet getResourceRecordSet() {
            return resourceRecordSet;
        }
    }

}
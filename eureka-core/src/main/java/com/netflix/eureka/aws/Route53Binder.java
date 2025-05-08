package com.netflix.eureka.aws;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53ClientBuilder;

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

    private final Route53Client amazonRoute53Client;


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
        for (String domain : domains) {
            ResourceRecordSetWithHostedZone rrs = getResourceRecordSetWithHostedZone(domain);

            if (rrs != null) {
                if (rrs.getResourceRecordSet() == null) {
                    ResourceRecordSet resourceRecordSet = ResourceRecordSet.builder()
                            .name(domain)
                            .type(RRType.CNAME)
                            .ttl(serverConfig.getRoute53DomainTTL())
                            .build();
                    freeDomains.add(new ResourceRecordSetWithHostedZone(rrs.getHostedZone(), resourceRecordSet));
                } else if (NULL_DOMAIN.equals(rrs.getResourceRecordSet().resourceRecords().get(0).value())) {
                    freeDomains.add(rrs);
                }
                // already registered
                if (hasValue(rrs, registrationHostname)) {
                    return;
                }
            }
        }

        for (ResourceRecordSetWithHostedZone rrs : freeDomains) {
            if (createResourceRecordSet(rrs)) {
                logger.info("Bind {} to {}", registrationHostname, rrs.getResourceRecordSet().name());
                return;
            }
        }

        logger.warn("Unable to find free domain in {}", domains);
    }


    private boolean createResourceRecordSet(ResourceRecordSetWithHostedZone rrs) throws InterruptedException {
        ResourceRecordSet resourceRecordSet = rrs.getResourceRecordSet().toBuilder()
                .resourceRecords(ResourceRecord.builder().value(registrationHostname).build())
                .build();
        Change change = Change.builder()
                .action(ChangeAction.UPSERT)
                .resourceRecordSet(resourceRecordSet)
                .build();
        if (executeChangeWithRetry(change, rrs.getHostedZone())) {
            Thread.sleep(1000);
            // check change not overwritten
            ResourceRecordSet updatedResourceRecordSet = getResourceRecordSet(rrs.getResourceRecordSet().name(), rrs.getHostedZone());
            if (updatedResourceRecordSet != null) {
                return updatedResourceRecordSet.resourceRecords().equals(resourceRecordSet.resourceRecords());
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
        ChangeBatch changeBatch = ChangeBatch.builder()
                .changes(change)
                .build();
        ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest = ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(hostedZone.id())
                .changeBatch(changeBatch)
                .build();

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
        ListResourceRecordSetsRequest request = ListResourceRecordSetsRequest.builder()
                .maxItems(String.valueOf(Integer.MAX_VALUE))
                .hostedZoneId(hostedZone.id())
                .build();

        ListResourceRecordSetsResponse listResourceRecordSetsResult = amazonRoute53Client.listResourceRecordSets(request);

        for (ResourceRecordSet rrs : listResourceRecordSetsResult.resourceRecordSets()) {
            if (rrs.name().equals(domain)) {
                return rrs;
            }
        }

        return null;
    }

    private HostedZone getHostedZone(String domain) {
        ListHostedZonesRequest listHostedZoneRequest = ListHostedZonesRequest.builder()
                .maxItems(String.valueOf(Integer.MAX_VALUE))
                .build();
        ListHostedZonesResponse listHostedZonesResult = amazonRoute53Client.listHostedZones(listHostedZoneRequest);
        for (HostedZone hostedZone : listHostedZonesResult.hostedZones()) {
            if (domain.endsWith(hostedZone.name())) {
                return hostedZone;
            }
        }
        return null;
    }

    private void unbindFromDomain(String domain) throws InterruptedException {
        ResourceRecordSetWithHostedZone resourceRecordSetWithHostedZone = getResourceRecordSetWithHostedZone(domain);
        if (hasValue(resourceRecordSetWithHostedZone, registrationHostname)) {
            ResourceRecordSet updatedResourceRecordSet = resourceRecordSetWithHostedZone.getResourceRecordSet().toBuilder()
                    .resourceRecords(ResourceRecord.builder().value(NULL_DOMAIN).build())
                    .build();
            Change change = Change.builder()
                    .action(ChangeAction.UPSERT)
                    .resourceRecordSet(updatedResourceRecordSet)
                    .build();
            executeChangeWithRetry(change, resourceRecordSetWithHostedZone.getHostedZone());
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

        amazonRoute53Client.close();
    }

    private Route53Client getAmazonRoute53Client(EurekaServerConfig serverConfig) {
        String awsAccessId = serverConfig.getAWSAccessId();
        String awsSecretKey = serverConfig.getAWSSecretKey();

        Route53ClientBuilder route53ClientBuilder;
        if (awsAccessId != null && !awsAccessId.isEmpty() && awsSecretKey != null && !awsSecretKey.isEmpty()) {
            AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(awsAccessId, awsSecretKey);
            route53ClientBuilder = Route53Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
                    .region(Region.of(clientConfig.getRegion().trim().toLowerCase()));
        } else {
            route53ClientBuilder = Route53Client.builder()
                    .credentialsProvider(InstanceProfileCredentialsProvider.create())
                    .region(Region.of(clientConfig.getRegion().trim().toLowerCase()));
        }

        return route53ClientBuilder.build();
    }

    private boolean hasValue(ResourceRecordSetWithHostedZone resourceRecordSetWithHostedZone, String ip) {
        if (resourceRecordSetWithHostedZone != null && resourceRecordSetWithHostedZone.getResourceRecordSet() != null) {
            ResourceRecordSet recordSet = resourceRecordSetWithHostedZone.getResourceRecordSet();
            for (ResourceRecord rr : recordSet.resourceRecords()) {
                if (ip.equals(rr.value())) {
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
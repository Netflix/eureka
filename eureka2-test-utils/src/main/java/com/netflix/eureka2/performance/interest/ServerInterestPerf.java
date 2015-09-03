package com.netflix.eureka2.performance.interest;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.model.toplogy.ServiceTopologyGenerator;
import com.netflix.eureka2.model.toplogy.TopologyDataProviders;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.InterestChannelImpl;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.metric.server.EurekaServerMetricFactory.serverMetrics;

/**
 * Test performance of server side interest channel/Eureka registry. Test registrations are injected
 * directly into the registry, and {@link com.netflix.eureka2.server.channel.InterestChannelImpl} instances are
 * instantiated without real underlying transport.
 *
 * @author Tomasz Bak
 */
public class ServerInterestPerf {

    private static final Logger logger = LoggerFactory.getLogger(ServerInterestPerf.class);

    private final ServiceTopologyGenerator serviceTopology;
    private final ConcurrentLinkedQueue<InstanceInfo> servicePool;
    private final Iterator<Interest<InstanceInfo>> clientInterestIt;

    private final EurekaRegistry<InstanceInfo> eurekaRegistry = new EurekaRegistryImpl(registryMetrics());

    private final PerformanceScoreBoard scoreBoard = new PerformanceScoreBoard();
    private final Configuration config;

    private Subscription registryUpdateSubscription;
    private Subscription scoreBoardSubscription;

    public ServerInterestPerf(Configuration config) {
        this.config = config;
        this.serviceTopology = TopologyDataProviders.serviceTopologyGeneratorFor("perfTest", (int) config.getTargetRegistryLevel());
        this.servicePool = new ConcurrentLinkedQueue<>(serviceTopology.serviceList());
        this.clientInterestIt = serviceTopology.clientInterestIterator();
    }

    private void startRegistryUpdates() {
        long interval = config.getRegistrationRateUnit().toMillis(1) / config.getRegistrationRate();
        long reqPerInterval = 1;
        if (interval == 0) {
            interval = 1;
            reqPerInterval = config.getRegistrationRate() / 1000;
        }

        final Queue<InstanceInfo> activeRegistrationsQueue = new ConcurrentLinkedQueue<>();
        final Source source = new Source(Origin.LOCAL, "perf");
        final long finalReqPerInterval = reqPerInterval;
        registryUpdateSubscription = Observable.interval(interval, TimeUnit.MILLISECONDS).flatMap(new Func1<Long, Observable<Void>>() {
            @Override
            public Observable<Void> call(Long tick) {
                Observable<Void> regResult = registerBatchOfInstances(activeRegistrationsQueue, source, finalReqPerInterval);
                Observable<Void> unregResult = unregisterBatchOfInstances(activeRegistrationsQueue, source, finalReqPerInterval);
                return regResult.concatWith(unregResult);
            }
        }).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Data injection terminated");
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Data injection terminated due to an error", e);
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    private Observable<Void> registerBatchOfInstances(final Queue<InstanceInfo> activeRegistrationsQueue, final Source source, final long batchSize) {
        final PublishSubject<ChangeNotification<InstanceInfo>> instanceSubject = PublishSubject.create();
        return eurekaRegistry.connect(source, instanceSubject)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        for (int i = 0; i < batchSize; i++) {
                            InstanceInfo instanceInfo = servicePool.poll();
                            if (instanceInfo == null) {
                                logger.warn("Run out of available instance info objects");
                                break;
                            }
                            logger.info("Registered instance {}", instanceInfo.getId());

                            scoreBoard.processedRegistrationIncrement();
                            scoreBoard.setRegistrySize(eurekaRegistry.size());

                            activeRegistrationsQueue.add(instanceInfo);
                            instanceSubject.onNext(new SourcedChangeNotification<>(ChangeNotification.Kind.Add, instanceInfo, source));
                        }

                    }
                });
    }

    private Observable<Void> unregisterBatchOfInstances(final Queue<InstanceInfo> activeRegistrationsQueue, final Source source, final long batchSize) {
        final PublishSubject<ChangeNotification<InstanceInfo>> instanceSubject = PublishSubject.create();
        return eurekaRegistry.connect(source, instanceSubject)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        for (int i = 0; i < batchSize; i++) {
                            InstanceInfo instanceInfo = servicePool.poll();
                            if (instanceInfo == null) {
                                logger.warn("Run out of available instance info objects");
                                break;
                            }
                            logger.info("Registered instance {}", instanceInfo.getId());

                            scoreBoard.processedRegistrationIncrement();
                            scoreBoard.setRegistrySize(eurekaRegistry.size());

                            activeRegistrationsQueue.add(instanceInfo);
                            instanceSubject.onNext(new SourcedChangeNotification<>(ChangeNotification.Kind.Delete, instanceInfo, source));
                        }

                    }
                });
    }

    public void startInterestSubscriptions() {
        long interval = config.getInterestRateUnit().toMillis(1) / config.getInterestSubscriptionRate();
        long reqPerInterval = 1;
        if (interval == 0) {
            interval = 1;
            reqPerInterval = config.getInterestSubscriptionRate() / 1000;
        }

        final Queue<InterestChannel> activeInterests = new ConcurrentLinkedQueue<>();
        final long finalReqPerInterval = reqPerInterval;
        registryUpdateSubscription = Observable.interval(interval, TimeUnit.MILLISECONDS).doOnNext(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                for (int i = 0; i < finalReqPerInterval; i++) {
                    InterestChannel interestChannel = createInterestSubscription();
                    activeInterests.add(interestChannel);
                    scoreBoard.processedInterestsIncrement();
                    scoreBoard.activeInterestsIncrement();
                }
                for (int i = 0; i < finalReqPerInterval && activeInterests.size() > config.getTargetInterestLevel(); i++) {
                    InterestChannel interestChannel = activeInterests.poll();
                    interestChannel.close();
                    scoreBoard.activeInterestsDecrement();
                }
            }
        }).ignoreElements().cast(Void.class).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Interest subscription injection terminated");
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Interest subscription injection terminated with an error", e);
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    private InterestChannel createInterestSubscription() {
        MockedMessageConnection transport = new MockedMessageConnection(scoreBoard);
        InterestChannelImpl interestChannel = new InterestChannelImpl(eurekaRegistry, transport, serverMetrics().getInterestChannelMetrics());
        transport.subscribeTo(clientInterestIt.next());
        return interestChannel;
    }

    public void startScoreBoardWatcher() {
        scoreBoardSubscription = Observable.timer(0, 5, TimeUnit.SECONDS)
                .doOnNext(new Action1<Long>() {
                    @Override
                    public void call(Long tick) {
                        scoreBoard.renderScoreBoard(System.out);
                    }
                }).subscribe();
    }

    private void stopAll() {
        if (registryUpdateSubscription != null) {
            registryUpdateSubscription.unsubscribe();
        }
        if (scoreBoardSubscription != null) {
            scoreBoardSubscription.unsubscribe();
        }
    }

    static class Configuration {

        private long registrationRate = 10;
        private TimeUnit registrationRateUnit = TimeUnit.SECONDS;
        private long targetRegistryLevel = 10000;

        private long interestSubscriptionRate = 1;
        private TimeUnit interestRateUnit = TimeUnit.SECONDS;
        private long targetInterestLevel = 2000;

        private long testDurationSec = 5 * 60 * 1000;

        public int getRegistrationRate() {
            return (int) registrationRate;
        }

        public TimeUnit getRegistrationRateUnit() {
            return registrationRateUnit;
        }

        public long getTargetRegistryLevel() {
            return targetRegistryLevel;
        }

        public int getInterestSubscriptionRate() {
            return (int) interestSubscriptionRate;
        }

        public TimeUnit getInterestRateUnit() {
            return interestRateUnit;
        }

        public long getTargetInterestLevel() {
            return targetInterestLevel;
        }

        public int getTestDuration() {
            return (int) (testDurationSec * 1000);
        }

        @Override
        public String toString() {
            return "Configuration{" +
                    "registrationRate=" + registrationRate +
                    ", registrationRateUnit=" + registrationRateUnit +
                    ", targetRegistryLevel=" + targetRegistryLevel +
                    ", interestSubscriptionRate=" + interestSubscriptionRate +
                    ", interestRateUnit=" + interestRateUnit +
                    ", targetInterestLevel=" + targetInterestLevel +
                    ", testDurationSec=" + testDurationSec +
                    '}';
        }
    }

    static Configuration parseCommandLineArgs(String[] args) {
        Options options = new Options()
                .addOption("h", false, "print this help information")
                .addOption(
                        OptionBuilder.withLongOpt("reg-rate").hasArg().withType(Number.class)
                                .withDescription("Registration rate (req/sec)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("reg-target").hasArg().withType(Number.class)
                                .withDescription("Target registration size").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("interest-rate").hasArg().withType(Number.class)
                                .withDescription("Interest subscription rate (req/min)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("interest-target").hasArg().withType(Number.class)
                                .withDescription("Target interest level").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("test-duration").hasArg().withType(Number.class)
                                .withDescription("Duration of test (sec)").create()
                );

        Configuration config = new Configuration();
        try {
            CommandLine cli = new PosixParser().parse(options, args);

            if (cli.hasOption("-h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("ServerInterestPerf", "Options:", options, null, true);
                System.out.println();
                System.out.println("For example:");
                System.out.println();
                System.out.println("    ServerInterestPerf --reg-rate 10s --reg-target 1000 --interest-rate 10s --interest-target 1000 --test-duration 600");
                return null;
            }

            if (cli.hasOption("--reg-rate")) {
                config.registrationRate = (long) cli.getParsedOptionValue("reg-rate");
            }
            if (cli.hasOption("--reg-target")) {
                config.targetRegistryLevel = (long) cli.getParsedOptionValue("reg-target");
            }
            if (cli.hasOption("--interest-rate")) {
                config.interestSubscriptionRate = (long) cli.getParsedOptionValue("interest-rate");
            }
            if (cli.hasOption("--interest-target")) {
                config.targetInterestLevel = (long) cli.getParsedOptionValue("interest-target");
            }
            if (cli.hasOption("--test-duration")) {
                config.testDurationSec = (long) cli.getParsedOptionValue("test-duration");
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid command line parameters; " + e);
        }

        return config;
    }

    public static void main(String[] args) {

        Configuration config = parseCommandLineArgs(args);
        if (config == null) {
            return;
        }

        logger.info("Running ServerInterestPerf with configuration {}", config);

        ServerInterestPerf perf = new ServerInterestPerf(config);
        perf.startRegistryUpdates();
        perf.startInterestSubscriptions();
        perf.startScoreBoardWatcher();

        logger.info("Test execution time is {}[sec]...", config.getTestDuration() / 1000);
        try {
            Thread.sleep(config.getTestDuration());
        } catch (InterruptedException e) {
            // IGNORE
        }
        logger.info("Finishing the test. All actors will be terminated...");
        perf.stopAll();
        logger.info("Exiting");
    }
}

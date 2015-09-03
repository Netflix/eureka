package com.netflix.eureka2.performance.cluster;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.model.toplogy.ServiceTopologyGenerator;
import com.netflix.eureka2.model.toplogy.TopologyDataProviders;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * Eureka2 embedded cluster performance benchmarking tool.
 *
 * @author Tomasz Bak
 */
public class EurekaPerf {

    private static final Logger logger = LoggerFactory.getLogger(EurekaPerf.class);

    private static final int LATENCY_THRESHOLD = 1000;

    private final EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();

    private final Configuration config;

    private final EurekaDeployment eurekaDeployment;

    private Subscription scoreBoardSubscription;

    private final Scheduler scheduler;

    private final PublishSubject<Observable<ChangeNotification<InstanceInfo>>> expectedObservablesSubject = PublishSubject.create();
    private final Observable<ChangeNotification<InstanceInfo>> expectedNotifications = Observable.merge(expectedObservablesSubject).share();

    private final ServiceTopologyGenerator serviceTopology;
    private final ConcurrentLinkedQueue<InstanceInfo> servicePool;
    private final Iterator<Interest<InstanceInfo>> clientInterestIt;

    private final PerformanceScoreBoard scoreBoard = new PerformanceScoreBoard();

    private Subscription registrationActorsSubscription;
    private Subscription interestActorsSubscription;
    private final Set<ClientActor> liveActors = new ConcurrentSkipListSet<>();

    public EurekaPerf(Configuration config, Scheduler scheduler) {
        this.config = config;
        this.scheduler = scheduler;
        this.eurekaDeployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(config.getWriteClusterSize())
                .withReadClusterSize(config.getReadClusterSize())
                .withDeploymentView(true)
                .withEphemeralPorts(false)
                .withTransportConfig(transportConfig)
                .build();
        this.serviceTopology = TopologyDataProviders.serviceTopologyGeneratorFor(
                "perfTest", config.getRegistrationRate(), config.getRegistrationDuration(), config.getMaxRegistrations()
        );
        this.servicePool = new ConcurrentLinkedQueue<>(serviceTopology.serviceList());
        this.clientInterestIt = serviceTopology.clientInterestIterator();
    }

    public void startMultipleInterestClients() {
        if (config.getInterestSubscriptionRate() <= 0) {
            return;
        }
        final AtomicInteger activeInterestClients = new AtomicInteger();
        interestActorsSubscription = Observable.timer(0, 60 * 1000 / config.getInterestSubscriptionRate(), TimeUnit.MILLISECONDS)
                .flatMap(new Func1<Long, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Long tick) {
                        if (activeInterestClients.get() >= config.getMaxInterestSubscription()) {
                            return Observable.empty();
                        }
                        Interest<InstanceInfo> interest = config.isFullFetchClients() ? Interests.forFullRegistry() : clientInterestIt.next();

                        EurekaInterestClient interestClient;
                        if (config.readClusterSize == 0) {
                            interestClient = eurekaDeployment.interestClientToWriteServer((int) (tick % config.getWriteClusterSize()));
                        } else {
                            interestClient = eurekaDeployment.interestClientToReadServer((int) (tick % config.getReadClusterSize()));
                        }
                        LatencyVeryfingInterestActor interestActor = new LatencyVeryfingInterestActor(
                                interestClient,
                                interest,
                                expectedNotifications,
                                config.getInterestSubscriptionDuration(),
                                LATENCY_THRESHOLD,
                                scoreBoard,
                                scheduler
                        );
                        addLiveActor(interestActor);
                        return Observable.empty();
                    }
                })
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Interest subscription actors completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.info("Interest subscription actors completed with error", e);
                    }

                    @Override
                    public void onNext(Void data) {
                        // No-op
                    }
                });
    }

    public void startRegistrations() {
        final AtomicInteger activeRegistrations = new AtomicInteger();
        registrationActorsSubscription = Observable.timer(0, 1000 / config.registrationRate, TimeUnit.MILLISECONDS)
                .flatMap(new Func1<Long, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Long tick) {
                        if (activeRegistrations.get() >= config.maxRegistrations) {
                            return Observable.empty();
                        }
                        final InstanceInfo instanceInfo = servicePool.poll();
                        if (instanceInfo == null) {
                            logger.info("Run out of available instance info objects");
                            return Observable.empty();
                        }

                        EurekaRegistrationClient registrationClient = eurekaDeployment.registrationClientToWriteServer((int) (tick % config.writeClusterSize));
                        try {
                            RegisteringActor registeringActor = new RegisteringActor(registrationClient, config.getRegistrationDuration(), instanceInfo, scoreBoard, scheduler);
                            activeRegistrations.incrementAndGet();

                            Observable<ChangeNotification<InstanceInfo>> registrationNotifications = registeringActor
                                    .registrationNotifications()
                                    .doOnTerminate(new Action0() {
                                        @Override
                                        public void call() {
                                            activeRegistrations.decrementAndGet();
                                            servicePool.add(serviceTopology.replacementFor(instanceInfo));
                                        }
                                    });
                            expectedObservablesSubject.onNext(registrationNotifications);
                            registeringActor.start();

                            addLiveActor(registeringActor);
                        } catch (Exception e) {
                            logger.error("Failed to startup a new registration actor", e);
                        }

                        return Observable.empty();
                    }
                })
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Registering actors completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.info("Registering actors completed with error", e);
                    }

                    @Override
                    public void onNext(Void data) {
                        // No-op
                    }
                });
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

    public void stopAll() {
        if (registrationActorsSubscription != null) {
            registrationActorsSubscription.unsubscribe();
        }
        if (interestActorsSubscription != null) {
            interestActorsSubscription.unsubscribe();
        }
        if (scoreBoardSubscription != null) {
            scoreBoardSubscription.unsubscribe();
        }
        for (ClientActor actor : liveActors) {
            actor.stop();
        }
    }

    private void addLiveActor(final ClientActor clientActor) {
        liveActors.add(clientActor);
        clientActor.lifecycle().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                liveActors.remove(clientActor);
            }

            @Override
            public void onError(Throwable e) {
                liveActors.remove(clientActor);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
    }

    static class Configuration {
        private long writeClusterSize = 2;
        private long readClusterSize = 2;

        private long registrationRate = 10;
        private long registrationUpdateIntervalSec = 1000;
        private long registrationDurationSec = 1 * 60 * 1000;
        private long maxRegistrations = 1000;

        private boolean fullFetchClients;

        private long interestSubscriptionRate = 1;
        private long interestSubscriptionDurationSec = 10 * 60 * 1000;
        private long maxInterestSubscriptions = 100;

        private long testDurationSec = 5 * 60 * 1000;

        public int getWriteClusterSize() {
            return (int) writeClusterSize;
        }

        public int getReadClusterSize() {
            return (int) readClusterSize;
        }

        public int getRegistrationRate() {
            return (int) registrationRate;
        }

        public int getRegistrationUpdateInterval() {
            return (int) (registrationUpdateIntervalSec * 1000);
        }

        public int getRegistrationDuration() {
            return (int) (registrationDurationSec * 1000);
        }

        public int getMaxRegistrations() {
            return (int) maxRegistrations;
        }

        public boolean isFullFetchClients() {
            return fullFetchClients;
        }

        public int getInterestSubscriptionRate() {
            return (int) interestSubscriptionRate;
        }

        public int getInterestSubscriptionDuration() {
            return (int) (interestSubscriptionDurationSec * 1000);
        }

        public int getMaxInterestSubscription() {
            return (int) maxInterestSubscriptions;
        }

        public int getTestDuration() {
            return (int) (testDurationSec * 1000);
        }

        @Override
        public String toString() {
            return "Configuration{" +
                    "writeClusterSize=" + writeClusterSize +
                    ", readClusterSize=" + readClusterSize +
                    ", registrationRate=" + registrationRate +
                    ", registrationUpdateInterval=" + registrationUpdateIntervalSec + "[sec]" +
                    ", registrationDuration=" + registrationDurationSec + "[sec]" +
                    ", maxRegistrations=" + maxRegistrations +
                    ", fullFetchClients=" + fullFetchClients +
                    ", interestSubscriptionRate=" + interestSubscriptionRate +
                    ", interestSubscriptionDuration=" + interestSubscriptionDurationSec + "[sec]" +
                    ", maxInterestSubscriptions=" + maxInterestSubscriptions +
                    ", testDuration=" + testDurationSec +
                    '}';
        }
    }

    static Configuration parseCommandLineArgs(String[] args) {
        Options options = new Options()
                .addOption("h", false, "print this help information")
                .addOption(
                        OptionBuilder.withLongOpt("write-size").hasArg().withType(Number.class)
                                .withDescription("Size of the write cluster").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("read-size").hasArg().withType(Number.class)
                                .withDescription("Size of the read cluster").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("reg-rate").hasArg().withType(Number.class)
                                .withDescription("Registration rate (req/sec)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("reg-update").hasArg().withType(Number.class)
                                .withDescription("Registration update interval (sec)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("reg-duration").hasArg().withType(Number.class)
                                .withDescription("Registration duration (sec)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("reg-max").hasArg().withType(Number.class)
                                .withDescription("Maximum number of active registrations").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("full-fetch")
                                .withDescription("Create full registry fetch subscription").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("interest-rate").hasArg().withType(Number.class)
                                .withDescription("Interest subscription rate (req/min)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("interest-duration").hasArg().withType(Number.class)
                                .withDescription("Interest subscription duration (sec)").create()
                )
                .addOption(
                        OptionBuilder.withLongOpt("interest-max").hasArg().withType(Number.class)
                                .withDescription("Maximum number of multiple interest clients").create()
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
                formatter.printHelp("EurekaPerf", "Options:", options, null, true);
                System.out.println();
                System.out.println("For example:");
                System.out.println();
                System.out.println("    EurekaPerf --write-size 2 --read-size 2 --reg-rate 10 --reg-duration 180 --reg-max 100 --interest-rate 1 --interest-duration 180 --interest-max 100 --test-duration 600");
                return null;
            }

            if (cli.hasOption("--write-size")) {
                config.writeClusterSize = (long) cli.getParsedOptionValue("write-size");
            }
            if (cli.hasOption("--read-size")) {
                config.readClusterSize = (long) cli.getParsedOptionValue("read-size");
            }
            if (cli.hasOption("--reg-rate")) {
                config.registrationRate = (long) cli.getParsedOptionValue("reg-rate");
            }
            if (cli.hasOption("--reg-update")) {
                config.registrationUpdateIntervalSec = (long) cli.getParsedOptionValue("reg-update");
            }
            if (cli.hasOption("--reg-duration")) {
                config.registrationDurationSec = (long) cli.getParsedOptionValue("reg-duration");
            }
            if (cli.hasOption("--reg-max")) {
                config.maxRegistrations = (long) cli.getParsedOptionValue("reg-max");
            }
            if (cli.hasOption("--full-fetch")) {
                config.fullFetchClients = true;
            }
            if (cli.hasOption("--interest-rate")) {
                config.interestSubscriptionRate = (long) cli.getParsedOptionValue("interest-rate");
            }
            if (cli.hasOption("--interest-duration")) {
                config.interestSubscriptionDurationSec = (long) cli.getParsedOptionValue("interest-duration");
            }
            if (cli.hasOption("--interest-max")) {
                config.maxInterestSubscriptions = (long) cli.getParsedOptionValue("interest-max");
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

        logger.info("Running EurekaPerf with configuration {}", config);

        EurekaPerf perf = new EurekaPerf(config, Schedulers.computation());
        perf.startMultipleInterestClients();
        perf.startRegistrations();
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

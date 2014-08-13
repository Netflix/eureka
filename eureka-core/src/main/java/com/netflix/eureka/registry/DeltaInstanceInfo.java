package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;
import rx.Observable;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David Liu
 */
public class DeltaInstanceInfo {
    private final Map<String, Object> deltas;

    public DeltaInstanceInfo() {
        deltas = new HashMap<String, Object>();
    }

    public boolean addDelta(String name, Object value) {
        String lowercaseName = name.toLowerCase();
        if (!isSupported(lowercaseName)) {
            return false;
        }
        deltas.put(lowercaseName, value);
        return true;
    }

    public Map<String, Object> getDeltas() {
        return deltas;
    }

    public InstanceInfo applyTo(InstanceInfo instanceInfo) {
        return new InstanceInfo.Builder().withInstanceInfo(instanceInfo).withDeltaInstanceInfo(this).build();
    }

    /**
     * Return a stream of {@link ChangeNotification}s with InstanceInfo data each containing a single delta change
     * @param baseInstanceInfo the base InstanceInfo from which the deltas should be applied
     * @return a stream of {@link ChangeNotification}s with InstanceInfo data each containing a single delta change
     */
    public Observable<ChangeNotification<InstanceInfo>> forChanges(final InstanceInfo baseInstanceInfo) {
        return Observable.from(deltas.keySet())
                .map(new Func1<String, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(String s) {
                        InstanceInfo instanceInfo = new InstanceInfo.Builder()
                                .withInstanceInfo(baseInstanceInfo)
                                .withDelta(s, deltas.get(s))
                                .build();
                        return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Modify, instanceInfo);
                    }
                });
    }

    /**
     * Return a stream of {@link ChangeNotification}s with DeltaInstanceInfo data each containing a single delta change
     * @return a stream of {@link ChangeNotification}s with DeltaInstanceInfo data each containing a single delta change
     */
    public Observable<ChangeNotification<DeltaInstanceInfo>> forChanges() {
        return Observable.from(deltas.keySet())
                .map(new Func1<String, ChangeNotification<DeltaInstanceInfo>>() {
                    @Override
                    public ChangeNotification<DeltaInstanceInfo> call(String name) {
                        DeltaInstanceInfo singleDelta = new DeltaInstanceInfo();
                        if (!singleDelta.addDelta(name, deltas.get(name))) {
                            return null;
                        }
                        return new ChangeNotification<DeltaInstanceInfo>(ChangeNotification.Kind.Modify, singleDelta);
                    }
                }).filter(new Func1<ChangeNotification<DeltaInstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<DeltaInstanceInfo> deltaInstanceInfoChangeNotification) {
                        return (deltaInstanceInfoChangeNotification != null);
                    }
                });
    }

    // TODO: check argument type as well
    private boolean isSupported(String name) {
        if (!InstanceInfo.SETTERS.keySet().contains(name)) {
            return false;
        }
        return true;
    }
}

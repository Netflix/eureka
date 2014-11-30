package com.netflix.eureka2.client.resolver;

import rx.Observable;
import rx.functions.Func1;

/**
 * @author David Liu
 */
public class CompositeServerResolver implements ServerResolver {

    private final ServerResolver[] resolvers;

    public CompositeServerResolver(ServerResolver... resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public Observable<Server> resolve() {
        return Observable.from(resolvers).flatMap(new Func1<ServerResolver, Observable<Server>>() {
            @Override
            public Observable<Server> call(ServerResolver serverResolver) {
                return serverResolver.resolve();
            }
        });
    }
}

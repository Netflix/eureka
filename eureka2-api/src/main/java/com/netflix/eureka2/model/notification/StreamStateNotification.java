/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.notification;


import com.netflix.eureka2.internal.util.Asserts;
import com.netflix.eureka2.model.interest.Interest;

/**
 * Change notification type, used internally only, that carries information about
 * which atomic interest subscription the state notification belongs to.
 *
 * @author Tomasz Bak
 */
public class StreamStateNotification<T> extends ChangeNotification<T> {

    public enum BufferState {Unknown, BufferStart, BufferEnd}

    private final BufferState bufferState;
    private final Interest<T> interest;

    public StreamStateNotification(BufferState bufferState, Interest<T> interest) {
        super(Kind.BufferSentinel, null);

        Asserts.assertNonNull(bufferState, "batchingState");
        Asserts.assertNonNull(interest, "interest");

        this.bufferState = bufferState;
        this.interest = interest;
    }

    public BufferState getBufferState() {
        return bufferState;
    }

    public Interest<T> getInterest() {
        return interest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        StreamStateNotification that = (StreamStateNotification) o;

        if (bufferState != that.bufferState)
            return false;
        if (!interest.equals(that.interest))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + bufferState.hashCode();
        result = 31 * result + interest.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StreamStateNotification{" +
                "batchingState=" + bufferState +
                ", interest=" + interest +
                '}';
    }

    public static <T> StreamStateNotification<T> bufferStartNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferState.BufferStart, interest);
    }

    public static <T> StreamStateNotification<T> bufferEndNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferState.BufferEnd, interest);
    }
}

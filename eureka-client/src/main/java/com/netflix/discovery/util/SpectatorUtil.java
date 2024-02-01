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

package com.netflix.discovery.util;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.api.patterns.PolledMeter.Builder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpectatorUtil {

  private SpectatorUtil() {
  }

  public static long time() {
    return Spectator.globalRegistry().clock().monotonicTime();
  }

  public static long time(@Nonnull Timer timer) {
    return timer.clock().monotonicTime();
  }

  public static void record(@Nonnull Timer timer, long startTime) {
    timer.record(time(timer) - startTime, TimeUnit.NANOSECONDS);
  }

  public static <T> T monitoredValue(@Nonnull String name, @Nonnull T obj,
      @Nonnull ToDoubleFunction<T> f) {
    return monitoredValue(name, null, obj, f);
  }

  /**
   * Creates a monitored value using the global registry and adds a "class" dimension
   */
  public static <T> T monitoredValue(@Nonnull String name, @Nullable String id, @Nonnull T obj,
      @Nonnull ToDoubleFunction<T> f) {
    return PolledMeter.using(Spectator.globalRegistry())
        .withName(name)
        .withTags(tags(id, obj.getClass()))
        .monitorValue(obj, f);
  }

  public static <T extends Number> T monitoredNumber(@Nonnull String name,
      @Nonnull Class<?> clazz, T number) {
    return monitoredNumber(name, null, clazz, number);
  }

  /**
   * Creates a monitored {@link Number} using the global registry and adds a "class" dimension
   */
  public static <T extends Number> T monitoredNumber(@Nonnull String name, @Nullable String id,
      @Nonnull Class<?> clazz, T number) {
    final Builder builder = PolledMeter.using(Spectator.globalRegistry())
        .withName(name)
        .withTag(classTag(clazz));

    if (id != null) {
      builder.withTag("id", id);
    }

    return builder.monitorValue(number);
  }

  public static Counter counter(@Nonnull String name, @Nonnull Class<?> clazz) {
    return Spectator.globalRegistry().counter(name, tags(null, clazz));
  }

  public static Counter counter(@Nonnull String name, @Nullable String id,
      @Nonnull Class<?> clazz) {
    return Spectator.globalRegistry().counter(name, tags(id, clazz));
  }

  public static Counter counter(@Nonnull String name, @Nullable String id,
      @Nonnull Class<?> clazz, Collection<Tag> extraTags) {
    return Spectator.globalRegistry().counter(name, tags(id, clazz, extraTags));
  }

  public static Timer timer(@Nonnull String name, @Nonnull Class<?> clazz) {
    return Spectator.globalRegistry().timer(name, tags(null, clazz));
  }

  public static Timer timer(@Nonnull String name, @Nullable String id, @Nonnull Class<?> clazz) {
    return Spectator.globalRegistry().timer(name, tags(id, clazz));
  }

  public static List<Tag> tags(@Nullable String id, @Nullable Class<?> clazz,
      @Nullable Collection<Tag> extraTags) {
    final List<Tag> tags = new ArrayList<>();
    if (clazz != null) {
      tags.add(classTag(clazz));
    }
    if (id != null) {
      tags.add(new BasicTag("id", id));
    }
    if (extraTags != null) {
      tags.addAll(extraTags);
    }
    return tags;
  }

  public static List<Tag> tags(@Nonnull Class<?> clazz) {
    return tags(null, clazz, null);
  }

  public static List<Tag> tags(@Nullable String id, @Nonnull Class<?> clazz) {
    return tags(id, clazz, null);
  }

  /**
   * Creates a monitored {@link AtomicLong} using the global registry and adds a "class" dimension
   */
  public static AtomicLong monitoredLong(@Nonnull String name, String id, @Nonnull Class<?> clazz) {
    return monitoredNumber(name, id, clazz, new AtomicLong());
  }

  public static AtomicLong monitoredLong(@Nonnull String name, @Nonnull Class<?> clazz) {
    return monitoredNumber(name, null, clazz, new AtomicLong());
  }

  public static Tag classTag(Class<?> c) {
    return new BasicTag("class", className(c));
  }

  private static String className(Class<?> c) {
    final String simpleName = c.getSimpleName();
    return simpleName.isEmpty() ? className(c.getEnclosingClass()) : simpleName;
  }
}

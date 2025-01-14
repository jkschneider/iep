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
package com.netflix.iep.platformservice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.archaius.config.CompositeConfig;
import com.netflix.archaius.config.EmptyConfig;
import com.netflix.archaius.config.PollingDynamicConfig;
import com.netflix.archaius.config.PollingStrategy;
import com.netflix.archaius.config.polling.FixedPollingStrategy;
import com.netflix.archaius.config.polling.PollingResponse;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.archaius.inject.RemoteLayer;
import com.netflix.archaius.typesafe.TypesafeConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Helper for configuring archaius with the Netflix dynamic property source.
 */
public final class PlatformServiceModule extends AbstractModule {

  @Override protected void configure() {
  }

  @Provides
  @Singleton
  Config providesTypesafeConfig() {
    final Config baseConfig = ConfigFactory.load();
    final String envConfigName = "iep-" + baseConfig.getString("netflix.iep.env.account-type");
    return ConfigFactory.load(envConfigName).withFallback(baseConfig);
  }

  @Provides
  @Singleton
  @RemoteLayer
  private com.netflix.archaius.Config providesOverrideConfig(Config application) throws Exception {
    return getDynamicConfig(application);
  }

  @Provides
  @Singleton
  @ApplicationLayer
  private CompositeConfig providesAppConfig(final Config application) throws Exception {
    CompositeConfig app = new CompositeConfig();
    app.addConfig("TYPESAFE", new TypesafeConfig(application));
    return app;
  }

  @Override public boolean equals(Object obj) {
    return obj != null && getClass().equals(obj.getClass());
  }

  @Override public int hashCode() {
    return getClass().hashCode();
  }

  private static Callable<PollingResponse> getCallback(Config cfg) throws Exception {
    final String prop = "netflix.iep.archaius.url";
    final URL url = URI.create(cfg.getString(prop)).toURL();
    return new PropertiesReader(url);
  }

  private static PollingStrategy getPollingStrategy(Config cfg) {
    final String propPollingInterval = "netflix.iep.archaius.polling-interval";
    final String propSyncInit = "netflix.iep.archaius.sync-init";
    final long interval = cfg.getDuration(propPollingInterval, TimeUnit.MILLISECONDS);
    return new FixedPollingStrategy(interval, TimeUnit.MILLISECONDS, cfg.getBoolean(propSyncInit));
  }

  public static com.netflix.archaius.Config getDynamicConfig(Config cfg) throws Exception {
    final String propUseDynamic = "netflix.iep.archaius.use-dynamic";
    return (cfg.getBoolean(propUseDynamic))
      ? new PollingDynamicConfig(getCallback(cfg), getPollingStrategy(cfg))
      : EmptyConfig.INSTANCE;
  }
}

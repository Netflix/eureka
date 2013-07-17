/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.discovery;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

public class EucalyptusEurekaClientConfig extends DefaultEurekaClientConfig implements EurekaClientConfig {
  private static final DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory
      .getInstance();

  @Override
  public String getEc2Endpoint( ) {
    String eucalyptusHost = configInstance.getStringProperty("eureka.eucalyptus.host", super.getEc2Endpoint( ))
    .get();
    return URI.create( "http://" + eucalyptusHost + ":8773/services/Eucalyptus" ).toASCIIString( );
  }

  @Override
  public String getAutoScalingEndpoint( ) {
    String eucalyptusHost = configInstance.getStringProperty("eureka.eucalyptus.host", super.getAutoScalingEndpoint( ))
    .get();
    return URI.create( "http://" + eucalyptusHost + ":8773/services/AutoScaling" ).toASCIIString( );
  }
}

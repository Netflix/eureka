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

package com.netflix.appinfo;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * A simple interface for indicating which <em>datacenter</em> a particular instance belongs.
 * 
 * @author Karthik Ranganathan
 *
 */
@XStreamAlias("datacenter")
public interface DataCenterInfo {
   enum Name {Netflix, Amazon, MyOwn};
   Name getName();
}

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.conf;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.resourcemanager.AdminService;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacitySchedulerConfiguration;
import org.apache.hadoop.yarn.webapp.dao.QueueConfigInfo;
import org.apache.hadoop.yarn.webapp.dao.SchedConfUpdateInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link MutableCSConfigurationProvider}.
 */
public class TestMutableCSConfigurationProvider {

  private MutableCSConfigurationProvider confProvider;
  private RMContext rmContext;
  private SchedConfUpdateInfo goodUpdate;
  private SchedConfUpdateInfo badUpdate;
  private CapacityScheduler cs;
  private AdminService adminService;

  private static final UserGroupInformation TEST_USER = UserGroupInformation
      .createUserForTesting("testUser", new String[] {});

  @Before
  public void setUp() {
    cs = mock(CapacityScheduler.class);
    rmContext = mock(RMContext.class);
    when(rmContext.getScheduler()).thenReturn(cs);
    when(cs.getConfiguration()).thenReturn(
        new CapacitySchedulerConfiguration());
    adminService = mock(AdminService.class);
    when(rmContext.getRMAdminService()).thenReturn(adminService);
    confProvider = new MutableCSConfigurationProvider(rmContext);
    goodUpdate = new SchedConfUpdateInfo();
    Map<String, String> goodUpdateMap = new HashMap<>();
    goodUpdateMap.put("goodKey", "goodVal");
    QueueConfigInfo goodUpdateInfo = new
        QueueConfigInfo("root.a", goodUpdateMap);
    goodUpdate.getUpdateQueueInfo().add(goodUpdateInfo);

    badUpdate = new SchedConfUpdateInfo();
    Map<String, String> badUpdateMap = new HashMap<>();
    badUpdateMap.put("badKey", "badVal");
    QueueConfigInfo badUpdateInfo = new
        QueueConfigInfo("root.a", badUpdateMap);
    badUpdate.getUpdateQueueInfo().add(badUpdateInfo);
  }

  @Test
  public void testInMemoryBackedProvider() throws Exception {
    Configuration conf = new Configuration();
    conf.set(YarnConfiguration.SCHEDULER_CONFIGURATION_STORE_CLASS,
        YarnConfiguration.MEMORY_CONFIGURATION_STORE);
    confProvider.init(conf);
    assertNull(confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.goodKey"));

    confProvider.logAndApplyMutation(TEST_USER, goodUpdate);
    confProvider.confirmPendingMutation(true);
    assertEquals("goodVal", confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.goodKey"));

    assertNull(confProvider.loadConfiguration(conf).get(
        "yarn.scheduler.capacity.root.a.badKey"));
    confProvider.logAndApplyMutation(TEST_USER, badUpdate);
    confProvider.confirmPendingMutation(false);
    assertNull(confProvider.loadConfiguration(conf).get(
        "yarn.scheduler.capacity.root.a.badKey"));

    confProvider.formatConfigurationInStore(conf);
    assertNull(confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.goodKey"));
  }

  @Test
  public void testRemoveQueueConfig() throws Exception {
    Configuration conf = new Configuration();
    conf.set(YarnConfiguration.SCHEDULER_CONFIGURATION_STORE_CLASS,
        YarnConfiguration.MEMORY_CONFIGURATION_STORE);
    confProvider.init(conf);

    SchedConfUpdateInfo updateInfo = new SchedConfUpdateInfo();
    Map<String, String> updateMap = new HashMap<>();
    updateMap.put("testkey1", "testval1");
    updateMap.put("testkey2", "testval2");
    QueueConfigInfo queueConfigInfo = new
        QueueConfigInfo("root.a", updateMap);
    updateInfo.getUpdateQueueInfo().add(queueConfigInfo);

    confProvider.logAndApplyMutation(TEST_USER, updateInfo);
    confProvider.confirmPendingMutation(true);
    assertEquals("testval1", confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.testkey1"));
    assertEquals("testval2", confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.testkey2"));

    // Unset testkey1.
    updateInfo = new SchedConfUpdateInfo();
    updateMap.put("testkey1", "");
    queueConfigInfo = new QueueConfigInfo("root.a", updateMap);
    updateInfo.getUpdateQueueInfo().add(queueConfigInfo);

    confProvider.logAndApplyMutation(TEST_USER, updateInfo);
    confProvider.confirmPendingMutation(true);
    assertNull("Failed to remove config",
        confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.testkey1"));
    assertEquals("testval2", confProvider.loadConfiguration(conf)
        .get("yarn.scheduler.capacity.root.a.testkey2"));
  }
}

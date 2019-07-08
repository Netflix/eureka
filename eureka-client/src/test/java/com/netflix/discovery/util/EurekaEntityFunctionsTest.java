/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class EurekaEntityFunctionsTest {

    private Application createSingleInstanceApp(
            String appId, String instanceId,
            InstanceInfo.ActionType actionType) {
        InstanceInfo instanceInfo = Mockito.mock(InstanceInfo.class);
        Mockito.when(instanceInfo.getId()).thenReturn(instanceId);
        Mockito.when(instanceInfo.getAppName()).thenReturn(instanceId);
        Mockito.when(instanceInfo.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);
        Mockito.when(instanceInfo.getActionType()).thenReturn(actionType);
        Application application = new Application(appId);
        application.addInstance(instanceInfo);
        return application;
    }

    private Applications createApplications(Application... applications) {
        return new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(applications)));
    }

    @Test
    public void testSelectApplicationNamesIfNotNullReturnNameString() {
        Applications applications = createApplications(new Application("foo"),
                new Application("bar"), new Application("baz"));

        HashSet<String> strings =
                new HashSet<>(Arrays.asList("baz", "bar", "foo"));
        Assert.assertEquals(strings,
                EurekaEntityFunctions.selectApplicationNames(applications));
    }

    @Test
    public void testSelectInstancesMappedByIdIfNotNullReturnMapOfInstances() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        HashMap<String, InstanceInfo> hashMap = new HashMap<>();
        hashMap.put("foo", application.getByInstanceId("foo"));
        Assert.assertEquals(hashMap,
                EurekaEntityFunctions.selectInstancesMappedById(application));
    }

    @Test
    public void testSelectInstanceIfInstanceExistsReturnSelectedInstance() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);

        Assert.assertNull(EurekaEntityFunctions
                .selectInstance(new Applications(), "foo"));
        Assert.assertNull(EurekaEntityFunctions
                .selectInstance(new Applications(), "foo", "foo"));

        Assert.assertEquals(application.getByInstanceId("foo"),
                EurekaEntityFunctions.selectInstance(applications, "foo"));
        Assert.assertEquals(application.getByInstanceId("foo"),
                EurekaEntityFunctions.selectInstance(applications, "foo", "foo"));
    }

    @Test
    public void testTakeFirstIfNotNullReturnFirstInstance() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);
        applications.addApplication(application);

        Assert.assertNull(EurekaEntityFunctions.takeFirst(new Applications()));
        Assert.assertEquals(application.getByInstanceId("foo"),
                EurekaEntityFunctions.takeFirst(applications));
    }

    @Test
    public void testSelectAllIfNotNullReturnAllInstances() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);
        applications.addApplication(application);
        Assert.assertEquals(new ArrayList<>(Arrays.asList(
                application.getByInstanceId("foo"),
                application.getByInstanceId("foo"))),
                EurekaEntityFunctions.selectAll(applications));
    }

    @Test
    public void testToApplicationMapIfNotNullReturnMapOfApplication() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Assert.assertEquals(1, EurekaEntityFunctions.toApplicationMap(
                new ArrayList<>(Arrays.asList(
                        application.getByInstanceId("foo")))).size());
    }

    @Test
    public void
    testToApplicationsIfNotNullReturnApplicationsFromMapOfApplication() {
        HashMap<String, Application> hashMap = new HashMap<>();
        hashMap.put("foo", new Application("foo"));
        hashMap.put("bar", new Application("bar"));
        hashMap.put("baz", new Application("baz"));

        Applications applications = createApplications(new Application("foo"),
                new Application("bar"), new Application("baz"));

        Assert.assertEquals(applications.size(),
                EurekaEntityFunctions.toApplications(hashMap).size());
    }

    @Test
    public void testToApplicationsIfNotNullReturnApplicationsFromInstances() {
        InstanceInfo instanceInfo1 = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED).getByInstanceId("foo");
        InstanceInfo instanceInfo2 = createSingleInstanceApp("bar", "bar",
                InstanceInfo.ActionType.ADDED).getByInstanceId("bar");
        InstanceInfo instanceInfo3 = createSingleInstanceApp("baz", "baz",
                InstanceInfo.ActionType.ADDED).getByInstanceId("baz");
        Assert.assertEquals(3, EurekaEntityFunctions.toApplications(
                instanceInfo1, instanceInfo2, instanceInfo3).size());
    }

    @Test
    public void testCopyApplicationsIfNotNullReturnApplications() {
        Application application1 = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Application application2 = createSingleInstanceApp("bar", "bar",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications();
        applications.addApplication(application1);
        applications.addApplication(application2);
        Assert.assertEquals(2,
                EurekaEntityFunctions.copyApplications(applications).size());
    }

    @Test
    public void testCopyApplicationIfNotNullReturnApplication() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Assert.assertEquals(1,
                EurekaEntityFunctions.copyApplication(application).size());
    }

    @Test
    public void testCopyInstancesIfNotNullReturnCollectionOfInstanceInfo() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Assert.assertEquals(1,
                EurekaEntityFunctions.copyInstances(
                        new ArrayList<>(Arrays.asList(
                                application.getByInstanceId("foo"))),
                        InstanceInfo.ActionType.ADDED).size());
    }

    @Test
    public void
    testMergeApplicationsIfNotNullAndHasAppNameReturnApplications() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);
        Assert.assertEquals(1, EurekaEntityFunctions.mergeApplications(
                applications, applications).size());
    }

    @Test
    public void
    testMergeApplicationsIfNotNullAndDoesNotHaveAppNameReturnApplications() {
        Application application1 = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications1 = createApplications(application1);

        Application application2 = createSingleInstanceApp("bar", "bar",
                InstanceInfo.ActionType.ADDED);
        Applications applications2 = createApplications(application2);

        Assert.assertEquals(2, EurekaEntityFunctions.mergeApplications(
                applications1, applications2).size());
    }

    @Test
    public void testMergeApplicationIfActionTypeAddedReturnApplication() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Assert.assertEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testMergeApplicationIfActionTypeModifiedReturnApplication() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.MODIFIED);
        Assert.assertEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testMergeApplicationIfActionTypeDeletedReturnApplication() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.DELETED);

        Assert.assertNotEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testUpdateMetaIfNotNullReturnApplications() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);
        Assert.assertEquals(1l,
                (long) EurekaEntityFunctions.updateMeta(applications)
                        .getVersion());
    }

    @Test
    public void testCountInstancesIfApplicationsHasInstancesReturnSize() {
        Application application = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED);
        Applications applications = createApplications(application);
        Assert.assertEquals(1,
                EurekaEntityFunctions.countInstances(applications));
    }

    @Test
    public void testComparatorByAppNameAndIdIfNotNullReturnInt() {
        InstanceInfo instanceInfo1 = Mockito.mock(InstanceInfo.class);
        InstanceInfo instanceInfo2 = Mockito.mock(InstanceInfo.class);
        InstanceInfo instanceInfo3 = createSingleInstanceApp("foo", "foo",
                InstanceInfo.ActionType.ADDED).getByInstanceId("foo");
        InstanceInfo instanceInfo4 = createSingleInstanceApp("bar", "bar",
                InstanceInfo.ActionType.ADDED).getByInstanceId("bar");

        Assert.assertTrue(EurekaEntityFunctions.comparatorByAppNameAndId()
                .compare(instanceInfo1, instanceInfo2) > 0);
        Assert.assertTrue(EurekaEntityFunctions.comparatorByAppNameAndId()
                .compare(instanceInfo3, instanceInfo2) > 0);
        Assert.assertTrue(EurekaEntityFunctions.comparatorByAppNameAndId()
                .compare(instanceInfo1, instanceInfo3) < 0);
        Assert.assertTrue(EurekaEntityFunctions.comparatorByAppNameAndId()
                .compare(instanceInfo3, instanceInfo4) > 0);
        Assert.assertTrue(EurekaEntityFunctions.comparatorByAppNameAndId()
                .compare(instanceInfo3, instanceInfo3) == 0);
    }
}

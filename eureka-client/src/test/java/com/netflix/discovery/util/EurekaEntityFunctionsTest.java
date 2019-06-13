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

    private InstanceInfo createSingleInstanceApp(
            String appId, String appName) {
        InstanceInfo instanceInfo = Mockito.mock(InstanceInfo.class);
        Mockito.when(instanceInfo.getId()).thenReturn(appId);
        Mockito.when(instanceInfo.getAppName()).thenReturn(appName);
        return instanceInfo;
    }

    @Test
    public void testSelectApplicationNamesIfNotNullReturnNameString() {
        Applications applications = new Applications();
        applications.addApplication(new Application("foo"));
        applications.addApplication(new Application("bar"));
        applications.addApplication(new Application("baz"));

        HashSet<String> strings =
                new HashSet<>(Arrays.asList("baz", "bar", "foo"));
        Assert.assertEquals(strings,
                EurekaEntityFunctions.selectApplicationNames(applications));
    }

    @Test
    public void testSelectInstancesMappedByIdIfNotNullReturnMapOfInstances() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application();
        application.addInstance(instanceInfo);

        HashMap<String, InstanceInfo> hashMap = new HashMap<>();
        hashMap.put("foo", instanceInfo);
        Assert.assertEquals(hashMap,
                EurekaEntityFunctions.selectInstancesMappedById(application));
    }

    @Test
    public void testSelectInstanceIfInstanceExistsReturnSelectedInstance() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));
        applications.addApplication(application);

        Assert.assertNull(EurekaEntityFunctions
                .selectInstance(new Applications(), "foo"));
        Assert.assertNull(EurekaEntityFunctions
                .selectInstance(new Applications(), "foo", "foo"));

        Assert.assertEquals(instanceInfo, EurekaEntityFunctions
                .selectInstance(applications, "foo"));
        Assert.assertEquals(instanceInfo, EurekaEntityFunctions
                .selectInstance(applications, "foo", "foo"));
    }

    @Test
    public void testTakeFirstIfNotNullReturnFirstInstance() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));
        applications.addApplication(application);

        Assert.assertNull(EurekaEntityFunctions.takeFirst(new Applications()));

        Assert.assertEquals(instanceInfo,
                EurekaEntityFunctions.takeFirst(applications));
    }

    @Test
    public void testSelectAllIfNotNullReturnAllInstances() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));
        applications.addApplication(application);

        Assert.assertEquals(
                new ArrayList<>(Arrays.asList(instanceInfo, instanceInfo)),
                EurekaEntityFunctions.selectAll(applications));
    }

    @Test
    public void testToApplicationMapIfNotNullReturnMapOfApplication() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);

        Assert.assertEquals(1,
                EurekaEntityFunctions.toApplicationMap(
                        new ArrayList<>(Arrays.asList(instanceInfo)))
                        .size());

        Assert.assertEquals(application.getInstances(),
                EurekaEntityFunctions.toApplicationMap(
                        new ArrayList<>(Arrays.asList(instanceInfo)))
                        .get("foo").getInstances());
    }

    @Test
    public void
    testToApplicationsIfNotNullReturnApplicationsFromMapOfApplication() {
        HashMap<String, Application> hashMap = new HashMap<>();
        hashMap.put("foo", new Application("foo"));
        hashMap.put("bar", new Application("bar"));
        hashMap.put("baz", new Application("baz"));

        Applications applications = new Applications(
                "appsHashCode", 1559658285l, new ArrayList<>());
        applications.addApplication(new Application("foo"));
        applications.addApplication(new Application("bar"));
        applications.addApplication(new Application("baz"));

        Assert.assertEquals(applications.size(),
                EurekaEntityFunctions.toApplications(hashMap).size());
    }

    @Test
    public void testToApplicationsIfNotNullReturnApplicationsFromInstances() {
        InstanceInfo instanceInfo1 = createSingleInstanceApp(
                "app1", "instance1");
        Mockito.when(instanceInfo1.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);

        InstanceInfo instanceInfo2 = createSingleInstanceApp(
                "app2", "instance2");
        Mockito.when(instanceInfo2.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);

        InstanceInfo instanceInfo3 = createSingleInstanceApp(
                "app3", "instance3");
        Mockito.when(instanceInfo3.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);

        Assert.assertEquals(3, EurekaEntityFunctions.toApplications(
                instanceInfo1, instanceInfo2, instanceInfo3).size());
    }

    @Test
    public void testCopyApplicationsIfNotNullReturnApplications() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));
        applications.addApplication(application);

        Assert.assertEquals(2,
                EurekaEntityFunctions.copyApplications(applications).size());
    }

    @Test
    public void testCopyApplicationIfNotNullReturnApplication() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);

        Assert.assertEquals(1,
                EurekaEntityFunctions.copyApplication(application).size());
    }

    @Test
    public void testCopyInstancesIfNotNullReturnCollectionOfInstanceInfo() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");

        Assert.assertEquals(1,
                EurekaEntityFunctions.copyInstances(
                        new ArrayList<>(Arrays.asList(instanceInfo)),
                        InstanceInfo.ActionType.ADDED).size());
    }

    @Test
    public void
    testMergeApplicationsIfNotNullAndHasAppNameReturnApplications() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);
        Mockito.when(instanceInfo.getActionType())
                .thenReturn(InstanceInfo.ActionType.ADDED);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));

        Assert.assertEquals(1, EurekaEntityFunctions.mergeApplications(
                applications, applications).size());
    }

    @Test
    public void
    testMergeApplicationsIfNotNullAndDoesNotHaveAppNameReturnApplications() {
        InstanceInfo instanceInfo1 = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo1.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);
        Mockito.when(instanceInfo1.getActionType())
                .thenReturn(InstanceInfo.ActionType.ADDED);

        Application application1 = new Application("foo");
        application1.addInstance(instanceInfo1);
        Applications applications1 = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application1)));

        InstanceInfo instanceInfo2 = createSingleInstanceApp("bar", "bar");
        Mockito.when(instanceInfo2.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);
        Mockito.when(instanceInfo2.getActionType())
                .thenReturn(InstanceInfo.ActionType.ADDED);

        Application application2 = new Application("bar");
        application2.addInstance(instanceInfo2);
        Applications applications2 = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application2)));

        Assert.assertEquals(2, EurekaEntityFunctions.mergeApplications(
                applications1, applications2).size());
    }

    @Test
    public void testMergeApplicationIfActionTypeAddedReturnApplication() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getActionType())
                .thenReturn(InstanceInfo.ActionType.ADDED);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);

        Assert.assertEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testMergeApplicationIfActionTypeModifiedReturnApplication() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getActionType())
                .thenReturn(InstanceInfo.ActionType.MODIFIED);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);

        Assert.assertEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testMergeApplicationIfActionTypeDeletedReturnApplication() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getActionType())
                .thenReturn(InstanceInfo.ActionType.DELETED);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);

        Assert.assertNotEquals(application.getInstances(),
                EurekaEntityFunctions.mergeApplication(
                        application, application).getInstances());
    }

    @Test
    public void testUpdateMetaIfNotNullReturnApplications() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Mockito.when(instanceInfo.getStatus())
                .thenReturn(InstanceInfo.InstanceStatus.UP);

        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));

        Assert.assertEquals(1l,
                (long) EurekaEntityFunctions.updateMeta(applications)
                        .getVersion());
    }

    @Test
    public void testCountInstancesIfApplicationsHasInstancesReturnSize() {
        InstanceInfo instanceInfo = createSingleInstanceApp("foo", "foo");
        Application application = new Application("foo");
        application.addInstance(instanceInfo);
        Applications applications = new Applications("appsHashCode",
                1559658285l, new ArrayList<>(Arrays.asList(application)));

        Assert.assertEquals(1,
                EurekaEntityFunctions.countInstances(applications));
    }

    @Test
    public void testComparatorByAppNameAndIdIfNotNullReturnInt() {
        InstanceInfo instanceInfo1 = Mockito.mock(InstanceInfo.class);
        InstanceInfo instanceInfo2 = Mockito.mock(InstanceInfo.class);
        InstanceInfo instanceInfo3 = createSingleInstanceApp("foo", "foo");
        InstanceInfo instanceInfo4 = createSingleInstanceApp("bar", "bar");

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

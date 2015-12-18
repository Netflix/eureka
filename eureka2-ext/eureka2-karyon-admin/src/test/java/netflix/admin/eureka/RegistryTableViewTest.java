package netflix.admin.eureka;

import java.util.Map;

import com.google.gson.JsonArray;
import netflix.adminresources.resources.eureka.registry.InstanceInfoSummary;
import netflix.adminresources.resources.eureka.registry.InstanceRegistryCache;
import netflix.adminresources.resources.eureka.registry.RegistryTableView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegistryTableViewTest {

    @Mock
    private InstanceRegistryCache registryCache;

    @Rule
    public ExternalResource mockRegistry = new MockRegistryResource() {
        @Override
        protected void before() throws Throwable {
            when(registryCache.get()).thenReturn(makeInstanceInfoMap());
        }
    };

    @Test(timeout = 60000)
    public void checkFilter() {
        final Map<String, InstanceInfoSummary> registryMap = registryCache.get();
        assertTrue(registryMap != null);
        assertEquals(5, registryMap.size());

        RegistryTableView registryView = new RegistryTableView(registryCache);
        registryView.setAllColumnsSearchTerm("APP_1");

        final JsonArray regData = registryView.getData();
        assertTrue(regData != null);
        assertTrue(regData.size() == 2);
    }

    @Test(timeout = 60000)
    public void checkPaginate() {
        final Map<String, InstanceInfoSummary> registryMap = registryCache.get();
        assertTrue(registryMap != null);
        assertEquals(5, registryMap.size());

        RegistryTableView registryView = new RegistryTableView(registryCache);
        registryView.setCurrentPageInfo(2, 2);

        final JsonArray regData = registryView.getData();
        assertTrue(regData != null);
        assertEquals(2, regData.size());
    }

    @Test(timeout = 60000)
    public void checkSorting() {
        final Map<String, InstanceInfoSummary> registryMap = registryCache.get();
        assertTrue(registryMap != null);
        assertEquals(5, registryMap.size());

        RegistryTableView registryView = new RegistryTableView(registryCache);
        registryView.enableColumnSort("Application", true);

        JsonArray regData = registryView.getData();
        assertTrue(regData != null);
        assertEquals(5, regData.size());

        String firstAppId = regData.get(0).getAsJsonObject().get("application").getAsString();
        assertEquals("App_3", firstAppId);

        registryView.enableColumnSort("Application", false);
        regData = registryView.getData();
        assertTrue(regData != null);
        assertEquals(5, regData.size());
        firstAppId = regData.get(0).getAsJsonObject().get("application").getAsString();
        assertEquals("App_1", firstAppId);
    }

    @Test(timeout = 60000)
    public void checkFilteredSize() {
        final Map<String, InstanceInfoSummary> registryMap = registryCache.get();
        assertTrue(registryMap != null);
        assertEquals(5, registryMap.size());

        RegistryTableView registryView = new RegistryTableView(registryCache);
        registryView.setAllColumnsSearchTerm("APP_1");
        final JsonArray regData = registryView.getData();
        assertTrue(regData != null);
        assertEquals(2, regData.size());

        assertEquals(2, registryView.getFilteredNumOfRecords());
        assertEquals(5, registryView.getTotalNumOfRecords());
    }
}

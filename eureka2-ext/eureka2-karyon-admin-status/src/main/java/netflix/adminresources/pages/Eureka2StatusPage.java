package netflix.adminresources.pages;

import netflix.adminresources.AbstractAdminPageInfo;
import netflix.adminresources.AdminPage;

/**
 * @author Tomasz Bak
 */
@AdminPage
public class Eureka2StatusPage extends AbstractAdminPageInfo {

    public static final String PAGE_ID = "eurekaStatus";
    public static final String NAME = "Eureka Status";

    public Eureka2StatusPage() {
        super(PAGE_ID, NAME);
    }

    @Override
    public String getPageTemplate() {
        return "/eureka2-status.ftl";
    }
}

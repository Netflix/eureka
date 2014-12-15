package netflix.admin;

import netflix.adminresources.AbstractAdminPageInfo;
import netflix.adminresources.AdminPage;

@AdminPage
public class EurekaPage extends AbstractAdminPageInfo {

    public static final String PAGE_ID = "eureka2";
    public static final String NAME = "Eureka2";

    public EurekaPage() {
        super(PAGE_ID, NAME);
    }

    @Override
    public String getPageTemplate() {
        return "/eureka-registry..ftl";
    }
}

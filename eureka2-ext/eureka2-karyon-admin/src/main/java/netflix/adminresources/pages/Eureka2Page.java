package netflix.adminresources.pages;

import netflix.adminresources.AbstractAdminPageInfo;
import netflix.adminresources.AdminPage;

@AdminPage
public class Eureka2Page extends AbstractAdminPageInfo {

    public static final String PAGE_ID = "eureka2";
    public static final String NAME = "Eureka2";

    public Eureka2Page() {
        super(PAGE_ID, NAME);
    }

    @Override
    public String getPageTemplate() {
        return "/eureka2-registry.ftl";
    }
}

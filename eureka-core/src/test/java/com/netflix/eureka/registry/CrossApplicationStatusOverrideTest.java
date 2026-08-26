package com.netflix.eureka.registry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.AbstractTester;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Regression tests for https://github.com/Netflix/eureka/issues/1630.
 *
 * The status-override map used to be keyed by a bare instance id. Since ids are entirely
 * client-chosen, two different applications could register instances with the same id,
 * and a status-update call made entirely within one application's own authorized
 * namespace would then flip the other application's instance status on its next renewal.
 */
public class CrossApplicationStatusOverrideTest extends AbstractTester {

    private static final String VICTIM_APP = "VICTIM-APP";
    private static final String ATTACKER_APP = "ATTACKER-APP";
    private static final String COLLIDING_INSTANCE_ID = "i-0123456789abcdef0";

    @Test
    public void statusUpdateInOneAppDoesNotOverrideCollidingIdInAnotherApp() {
        InstanceInfo victim = instanceWithAppAndId(VICTIM_APP, "victim-host", COLLIDING_INSTANCE_ID);
        InstanceInfo attacker = instanceWithAppAndId(ATTACKER_APP, "attacker-host", COLLIDING_INSTANCE_ID);

        registry.register(victim, 10000000, false);
        registry.register(attacker, 10000000, false);

        // the attacker only ever writes within its own app/id namespace, which it is
        // fully authorized to do
        boolean updated = registry.statusUpdate(
                ATTACKER_APP, COLLIDING_INSTANCE_ID, InstanceStatus.OUT_OF_SERVICE, null, false);
        assertThat(updated, is(true));

        // the victim's own, unrelated renewal must not pick up the attacker's override
        boolean renewed = registry.renew(VICTIM_APP, COLLIDING_INSTANCE_ID, false);
        assertThat(renewed, is(true));

        InstanceInfo victimAfterRenew = registry.getInstanceByAppAndId(VICTIM_APP, COLLIDING_INSTANCE_ID);
        assertThat(
                "victim instance status must not be affected by a status update the attacker made "
                        + "against a colliding id under a different app",
                victimAfterRenew.getStatus(), is(equalTo(InstanceStatus.UP)));

        InstanceInfo attackerAfterUpdate = registry.getInstanceByAppAndId(ATTACKER_APP, COLLIDING_INSTANCE_ID);
        assertThat(attackerAfterUpdate.getStatus(), is(equalTo(InstanceStatus.OUT_OF_SERVICE)));
    }

    @Test
    public void deleteStatusOverrideInOneAppDoesNotClearCollidingIdInAnotherApp() {
        InstanceInfo victim = instanceWithAppAndId(VICTIM_APP, "victim-host", COLLIDING_INSTANCE_ID);
        InstanceInfo attacker = instanceWithAppAndId(ATTACKER_APP, "attacker-host", COLLIDING_INSTANCE_ID);

        registry.register(victim, 10000000, false);
        registry.register(attacker, 10000000, false);

        // both the victim and the attacker independently set their own override on their
        // own app/id - two distinct overrides that happen to share an instance id
        registry.statusUpdate(VICTIM_APP, COLLIDING_INSTANCE_ID, InstanceStatus.OUT_OF_SERVICE, null, false);
        registry.statusUpdate(ATTACKER_APP, COLLIDING_INSTANCE_ID, InstanceStatus.OUT_OF_SERVICE, null, false);
        assertThat("expected one override entry per app", registry.getNumberofElementsininstanceCache(), is(2L));

        // clearing the override on the attacker's own app/id must not clear the victim's
        boolean deleted = registry.deleteStatusOverride(
                ATTACKER_APP, COLLIDING_INSTANCE_ID, InstanceStatus.UP, null, false);
        assertThat(deleted, is(true));

        assertThat(
                "deleting the attacker's own override must leave the victim's override entry intact",
                registry.getNumberofElementsininstanceCache(), is(1L));
        assertThat(
                "the surviving override entry must be scoped to the victim's app, not the attacker's",
                registry.overriddenInstanceStatusesSnapshot().keySet(),
                hasItem(VICTIM_APP + "/" + COLLIDING_INSTANCE_ID));
    }

    private InstanceInfo instanceWithAppAndId(String appName, String hostname, String id) {
        InstanceInfo base = createLocalInstanceWithIdAndStatus(hostname, id, InstanceStatus.UP);
        InstanceInfo.Builder builder = new InstanceInfo.Builder(base);
        builder.setAppName(appName);
        return builder.build();
    }
}

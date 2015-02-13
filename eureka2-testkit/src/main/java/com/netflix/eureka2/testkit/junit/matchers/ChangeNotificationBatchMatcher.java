package com.netflix.eureka2.testkit.junit.matchers;

import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * Verify that actual value passed to the matcher contains buffer change notification, followed
 * by data notifications (in any order), which is closed with bufferFinished change notification.
 *
 * @author Tomasz Bak
 */
public class ChangeNotificationBatchMatcher<T> extends BaseMatcher<List<ChangeNotification<T>>> {

    enum ErrorType {NoError, InvalidArgument, NoBufferStart, NotMatchingData, NoBufferEnd}

    private final List<ChangeNotification<T>> expectedData;
    private Object actualObject;
    private ErrorType errorType = ErrorType.NoError;
    private Matcher<Iterable<? extends ChangeNotification>> dataMatcher;

    public ChangeNotificationBatchMatcher(List<ChangeNotification<T>> expectedData) {
        this.expectedData = expectedData;
    }

    @Override
    public boolean matches(Object item) {
        actualObject = item;
        if (!(item instanceof List)) {
            errorType = ErrorType.InvalidArgument;
            return false;
        }
        List<ChangeNotification<T>> actual = (List<ChangeNotification<T>>) item;

        // Verify there is buffer notification
        if (actual.isEmpty() || actual.get(0).getKind() != Kind.BufferSentinel) {
            errorType = ErrorType.NoBufferStart;
            return false;
        }

        // Verify there is finish buffering notification
        ChangeNotification<T> lastItem = actual.get(actual.size() - 1);
        if (actual.size() == 1 || lastItem.getKind() != Kind.BufferSentinel) {
            errorType = ErrorType.NoBufferEnd;
            return false;
        }

        // Verify we have all expected data
        List<ChangeNotification<T>> actualData = actual.subList(1, actual.size() - 1);
        dataMatcher = containsInAnyOrder(expectedData.toArray(new ChangeNotification[expectedData.size()]));
        if (dataMatcher.matches(actualData)) {
            return true;
        }
        errorType = ErrorType.NotMatchingData;
        return false;
    }

    @Override
    public void describeTo(Description description) {
        switch (errorType) {
            case NoError:
                description.appendText("arguments match each other");
                break;
            case InvalidArgument:
                description.appendText("Unexpected type " + actualObject.getClass());
                break;
            case NoBufferStart:
                description.appendText("Kind.BufferStart ChangeNotification expected as a first item");
                break;
            case NotMatchingData:
                dataMatcher.describeTo(description);
                break;
            case NoBufferEnd:
                description.appendText("Kind.BufferSentinel ChangeNotification expected as a last item");
                break;
        }
    }
}

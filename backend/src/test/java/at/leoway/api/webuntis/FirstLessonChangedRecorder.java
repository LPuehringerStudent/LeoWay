package at.leoway.api.webuntis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test observer standing in for the leave-by recalculation (#58). */
@ApplicationScoped
public class FirstLessonChangedRecorder {
    private final List<FirstLessonChanged> events = new CopyOnWriteArrayList<>();

    /** A method, not a field: tests get a client proxy, whose fields are not the bean's. */
    public List<FirstLessonChanged> events() {
        return events;
    }

    void on(@Observes FirstLessonChanged e) {
        events.add(e);
    }
}

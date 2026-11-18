package at.leoway.api.leaveby;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;

/** Lets tests in other packages reset {@link LinzAgTrips} and read the trip fixture. */
@ApplicationScoped
public class LinzAgTripsAccess {
    @Inject
    LinzAgTrips trips;

    public void reset(Clock clock) {
        trips.clearCache();
        trips.useClock(clock);
    }

    /** The trip id of the {@code index}th trip in trip-arrive-by.json. */
    public static String parseFixtureTripId(int index) {
        try {
            return LinzAgTrips.parse(LinzAgTripsTest.fixture()).get(index).tripId();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

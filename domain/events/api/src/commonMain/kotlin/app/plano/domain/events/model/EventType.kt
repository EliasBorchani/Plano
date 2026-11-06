package app.plano.domain.events.model

/** The flat launch list from the PRD (27 types). Labels live in each UI's translations. */
enum class EventType {
    DINNER,
    LUNCH,
    BRUNCH,
    DRINKS,
    PARTY,
    BIRTHDAY,
    BARBECUE,
    PICNIC,
    GAME_NIGHT,
    MOVIE_NIGHT,
    CONCERT,
    FESTIVAL,
    SHOW,
    EXHIBITION,
    SPORTS_SESSION,
    HIKE,
    OUTDOOR_DAY,
    DAY_TRIP,
    WEEKEND_GETAWAY,
    HOLIDAY_TRIP,
    SKI_TRIP,
    ROAD_TRIP,
    BACHELOR_PARTY,
    BACHELORETTE_PARTY,
    WEDDING,
    CELEBRATION,
    OTHER,
    ;

    companion object {
        fun fromName(name: String): EventType? = entries.firstOrNull { it.name == name }
    }
}

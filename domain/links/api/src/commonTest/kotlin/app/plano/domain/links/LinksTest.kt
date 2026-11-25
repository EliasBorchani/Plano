package app.plano.domain.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Real links shared from the Google Maps app (owner, 2026-11-25). Address formats change without notice:
// add new samples here rather than editing these.
private const val PATHE =
    "https://www.google.com/maps/place/Path%C3%A9+%C3%8Ele+Seguin/@48.8221715,2.2349808,17z/data=!3m1!4b1!4m6!3m5!1s0x47e67b007c08e03b:0x7ed3505bdc74ce9a!8m2!3d48.8221715!4d2.2375611!16s%2Fg%2F11nr1sljhf?entry=tts&g_ep=EgoyMDI2MTEyMi4wIOC7DCoASAFQAw%3D%3D&skid=259944b6-608b-462a-9c4c-527461d21630"
private const val CROSSFIT =
    "https://www.google.com/maps/place/CrossFit+Le+Trap%C3%A8ze/@48.826446,2.2377877,17z/data=!3m2!4b1!5s0x47e67afa9a627779:0x8883d7fcd2d277d1!4m6!3m5!1s0x47e67afa823dc4ff:0xb8a8b479a113796e!8m2!3d48.826446!4d2.240368!16s%2Fg%2F11cjhl7rtz?entry=tts&g_ep=EgoyMDI2MTEyMi4wIOC7DCoASAFQAw%3D%3D&skid=1a5e0766-b949-4a6f-94d5-f86795348458"
private const val PADEL =
    "https://www.google.com/maps/place/4PADEL+Boulogne-Billancourt/@48.8281977,2.2398208,17z/data=!3m1!5s0x47e67afa9a627779:0x8883d7fcd2d277d1!4m6!3m5!1s0x47e67b13b6fdb1bf:0x96f4892f6a6a917a!8m2!3d48.8299075!4d2.24208!16s%2Fg%2F11xn7b9zrk?entry=tts&g_ep=EgoyMDI2MTEyMi4wIOC7DCoASAFQAw%3D%3D&skid=14b08f3c-7cf9-4d77-9e85-a6cb13cc5e8f"

class LinksTest {
    private fun card(text: String) = assertNotNull(Links.card(text), text)

    @Test
    fun googleMapsPlaceLinksGiveNameAndThePlacePosition() {
        val pathe = card(PATHE)
        assertEquals(LinkPlatform.GOOGLE_MAPS, pathe.platform)
        assertEquals("Pathé Île Seguin", pathe.title)
        assertEquals(PlaceHint("Pathé Île Seguin", Coordinates(48.8221715, 2.2375611), true), pathe.place)
        assertEquals("https://www.google.com/maps/place/Path%C3%A9+%C3%8Ele+Seguin/@48.8221715,2.2375611,17z", pathe.canonicalUrl)
        assertFalse(pathe.needsResolution)

        // Not the map view's center (@48.8281977,2.2398208): the place's own !3d/!4d.
        assertEquals(Coordinates(48.8299075, 2.24208), card(PADEL).place?.coordinates)
        assertEquals("4PADEL Boulogne-Billancourt", card(PADEL).title)
        assertEquals("CrossFit Le Trapèze", card(CROSSFIT).title)
        assertEquals(Coordinates(48.826446, 2.240368), card(CROSSFIT).place?.coordinates)
    }

    @Test
    fun trackingParametersAreStripped() {
        val url = card(PATHE).url
        assertFalse("entry=" in url || "g_ep=" in url || "skid=" in url, url)
        assertTrue(url.endsWith("!16s%2Fg%2F11nr1sljhf"), url)

        val shop = card("https://Shop.example.com/menu/?utm_source=wa&b=2&fbclid=x&a=1#top")
        assertEquals("https://shop.example.com/menu?a=1&b=2", shop.url)
    }

    @Test
    fun sameShopSharedTwiceIsOneOption() {
        val first = card("https://shop.example.com/menu?b=2&a=1&utm_medium=social")
        val second = card("shop.example.com/menu/?a=1&b=2&si=abc")
        assertEquals(first.canonicalUrl, second.canonicalUrl)

        // The same place shared from two map views.
        val again = PATHE.replace("@48.8221715,2.2349808,17z", "@48.83,2.24,13z").replace("259944b6", "00000000")
        assertEquals(card(PATHE).canonicalUrl, card(again).canonicalUrl)
    }

    @Test
    fun otherGoogleMapsForms() {
        val search = card("https://www.google.com/maps/search/Tour+Eiffel")
        assertEquals("Tour Eiffel", search.title)
        assertEquals("https://www.google.com/maps/search/?api=1&query=Tour+Eiffel", search.canonicalUrl)

        val pin = card("https://maps.google.fr/?q=48.8584,2.2945")
        assertNull(pin.title)
        assertEquals(PlaceHint(null, Coordinates(48.8584, 2.2945), true), pin.place)

        // Only the map view's center: a hint, not the place.
        val view = card("https://www.google.co.uk/maps/@48.8221715,2.2349808,15z")
        assertEquals(false, view.place?.coordinatesArePrecise)
        assertEquals(Coordinates(48.8221715, 2.2349808), view.place?.coordinates)

        assertEquals(LinkPlatform.OTHER, card("https://www.google.com/search?q=pathe").platform)
    }

    @Test
    fun shortLinksWaitForTheServer() {
        val short = card("https://maps.app.goo.gl/AbCdEf123?g_st=iw")
        assertTrue(short.needsResolution)
        assertEquals("https://maps.app.goo.gl/AbCdEf123", short.url)
        assertTrue(card("https://goo.gl/maps/xyz").needsResolution)
        assertFalse(card("https://goo.gl/other").needsResolution)
    }

    @Test
    fun theLinkIsFoundInsideShareText() {
        val text = "Pathé Île Seguin\n30 Quai Georges Gorse, Boulogne\nhttps://maps.app.goo.gl/AbCdEf123."
        assertEquals("https://maps.app.goo.gl/AbCdEf123", card(text).url)
    }

    @Test
    fun theEuConsentPageIsUnwrapped() {
        val consent = "https://consent.google.com/ml?continue=" + WebUrl.encode(PATHE) + "&gl=FR&hl=fr"
        assertEquals("Pathé Île Seguin", card(consent).title)
    }

    @Test
    fun genericCardsGuessATitleFromTheAddress() {
        val bistrot = card("https://www.example.com/fr/restaurants/le-petit-bistrot-48213.html")
        assertEquals(LinkPlatform.OTHER, bistrot.platform)
        assertEquals("Le petit bistrot", bistrot.title)
        assertTrue(bistrot.titleIsGuess)

        assertEquals("Soirée jazz", card("https://tickets.example/e/Soir%C3%A9e_jazz/9f8e7d6c5b4a39281706").title)
        assertEquals("example.com", card("https://www.example.com/").title)
        assertEquals("example.com", card("https://www.example.com/123456").title)
    }

    @Test
    fun aPageTitleReplacesOnlyAGuess() {
        val guessed = card("https://www.example.com/fr/le-petit-bistrot").withPage("  Le Petit Bistrot —\n Paris ", "Cuisine")
        assertEquals("Le Petit Bistrot — Paris", guessed.title)
        assertFalse(guessed.titleIsGuess)
        assertEquals("Cuisine", guessed.description)

        assertEquals("Pathé Île Seguin", card(PATHE).withPage("Google Maps", null).title)
    }

    @Test
    fun notLinks() {
        assertNull(Links.card("rendez-vous chez Léa"))
        assertNull(Links.card("ftp://example.com/file"))
        assertNull(Links.card("javascript:alert(1)"))
        // Reads as google.com, goes to evil.example.
        assertNull(Links.card("https://www.google.com@evil.example/maps"))
        assertNull(Links.card("https://exa mple.com"))
    }
}

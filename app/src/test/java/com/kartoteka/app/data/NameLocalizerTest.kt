package com.kartoteka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NameLocalizerTest {
    @Test fun namesFollowMessageLanguage() {
        assertEquals("Илья", NameLocalizer.firstName("Ілля", MessageLang.RU))
        assertEquals("Ілля", NameLocalizer.firstName("Илья", MessageLang.UK))
        assertEquals("Illia", NameLocalizer.firstName("Ілля", MessageLang.EN))
        assertEquals("Oleksandr", NameLocalizer.firstName("Александр", MessageLang.EN))
        assertEquals("Елена", NameLocalizer.firstName("Олена", MessageLang.RU))
        assertEquals("Максим", NameLocalizer.firstName("Максим", MessageLang.UK))
        assertEquals("Zhanna", NameLocalizer.firstName("Жанна", MessageLang.EN))
        assertEquals("John", NameLocalizer.firstName("John", MessageLang.RU))
    }
}

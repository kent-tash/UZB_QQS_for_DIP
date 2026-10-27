package com.example.uzb_qqs_for_dip.ui

import com.example.uzb_qqs_for_dip.ui.screens.EditableProfileField
import com.example.uzb_qqs_for_dip.util.toInitials
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileValidationTest {

    private fun validateCreateProfile(fullName: String, position: String): String? {
        val nameTrimmed = fullName.trim()
        val posTrimmed = position.trim()
        return if (nameTrimmed.isEmpty() || posTrimmed.isEmpty()) {
            "Заполните имя и должность"
        } else {
            null
        }
    }

    private fun validateEditSingleField(field: EditableProfileField, value: String): String? {
        val trimmed = value.trim()
        return if (field != EditableProfileField.ORGANIZATION && trimmed.isEmpty()) {
            "Поле не может быть пустым"
        } else {
            null
        }
    }

    @Test
    fun testCreateProfileValidation() {
        // Успешная валидация
        assertEquals(null, validateCreateProfile("Иванов Иван", "Бухгалтер"))
        assertEquals(null, validateCreateProfile("  Иванов Иван  ", "  Бухгалтер  "))

        // Ошибки валидации: пустые поля
        assertEquals("Заполните имя и должность", validateCreateProfile("", "Бухгалтер"))
        assertEquals("Заполните имя и должность", validateCreateProfile("   ", "Бухгалтер"))
        assertEquals("Заполните имя и должность", validateCreateProfile("Иванов Иван", ""))
        assertEquals("Заполните имя и должность", validateCreateProfile("Иванов Иван", "   "))
        assertEquals("Заполните имя и должность", validateCreateProfile("", ""))
    }

    @Test
    fun testEditSingleFieldValidation() {
        // Имя и должность не могут быть пустыми
        assertEquals("Поле не может быть пустым", validateEditSingleField(EditableProfileField.FULL_NAME, ""))
        assertEquals("Поле не может быть пустым", validateEditSingleField(EditableProfileField.FULL_NAME, "   "))
        assertEquals(null, validateEditSingleField(EditableProfileField.FULL_NAME, "Петров Петр"))

        assertEquals("Поле не может быть пустым", validateEditSingleField(EditableProfileField.POSITION, ""))
        assertEquals(null, validateEditSingleField(EditableProfileField.POSITION, "Главный бухгалтер"))

        // Организация может быть пустой
        assertEquals(null, validateEditSingleField(EditableProfileField.ORGANIZATION, ""))
        assertEquals(null, validateEditSingleField(EditableProfileField.ORGANIZATION, "   "))
        assertEquals(null, validateEditSingleField(EditableProfileField.ORGANIZATION, "ООО Вектор"))
    }

    @Test
    fun testInitialsGeneration() {
        assertEquals("Иванов И.И.", "Иванов Иван Иванович".toInitials())
        assertEquals("Петров П.", "Петров Петр".toInitials())
        assertEquals("Сидоров", "Сидоров".toInitials())
        assertEquals("Алимов С.А.", "  Алимов   Сардор   Азизович  ".toInitials())
        assertEquals("", "".toInitials())
        assertEquals("", "   ".toInitials())
    }
}

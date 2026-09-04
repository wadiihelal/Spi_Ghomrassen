package com.promoteur.app.service;

import com.promoteur.app.service.impl.AmountInWordsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the written amount on a receipt (UX-06): the figures and the words must always say
 * the same thing, in standard French.
 */
class AmountInWordsTest {

    private final AmountInWordsService service = new AmountInWordsServiceImpl();

    @Test
    @DisplayName("a whole amount of dinars is spelled without millimes")
    void aWholeAmountIsSpelledWithoutMillimes() {
        assertThat(this.service.spell(new BigDecimal("1230.000")))
                .isEqualTo("mille deux cent trente dinars");
    }

    @Test
    @DisplayName("millimes are spelled after the dinars")
    void millimesAreSpelledAfterTheDinars() {
        assertThat(this.service.spell(new BigDecimal("1230.500")))
                .isEqualTo("mille deux cent trente dinars et cinq cents millimes");
    }

    @Test
    @DisplayName("one dinar and one millime stay singular")
    void oneDinarAndOneMillimeStaySingular() {
        assertThat(this.service.spell(new BigDecimal("1.001"))).isEqualTo("un dinar et un millime");
    }

    @Test
    @DisplayName("zero is spelled rather than left blank")
    void zeroIsSpelledRatherThanLeftBlank() {
        assertThat(this.service.spell(BigDecimal.ZERO)).isEqualTo("zéro dinars");
    }

    @Test
    @DisplayName("seventy to ninety-nine follow standard French")
    void seventyToNinetyNineFollowStandardFrench() {
        assertThat(this.service.spell(new BigDecimal("70.000"))).startsWith("soixante-dix ");
        assertThat(this.service.spell(new BigDecimal("71.000"))).startsWith("soixante et onze ");
        assertThat(this.service.spell(new BigDecimal("77.000"))).startsWith("soixante-dix-sept ");
        assertThat(this.service.spell(new BigDecimal("80.000"))).startsWith("quatre-vingts ");
        assertThat(this.service.spell(new BigDecimal("81.000"))).startsWith("quatre-vingt-un ");
        assertThat(this.service.spell(new BigDecimal("91.000"))).startsWith("quatre-vingt-onze ");
        assertThat(this.service.spell(new BigDecimal("97.000"))).startsWith("quatre-vingt-dix-sept ");
    }

    @Test
    @DisplayName("twenty-one to sixty-one join with et, the rest with a hyphen")
    void twentyOneToSixtyOneJoinWithEt() {
        assertThat(this.service.spell(new BigDecimal("21.000"))).startsWith("vingt et un ");
        assertThat(this.service.spell(new BigDecimal("22.000"))).startsWith("vingt-deux ");
        assertThat(this.service.spell(new BigDecimal("61.000"))).startsWith("soixante et un ");
    }

    @Test
    @DisplayName("cent takes an s only when multiplied and final")
    void centTakesAnSOnlyWhenMultipliedAndFinal() {
        assertThat(this.service.spell(new BigDecimal("100.000"))).startsWith("cent ");
        assertThat(this.service.spell(new BigDecimal("200.000"))).startsWith("deux cents ");
        assertThat(this.service.spell(new BigDecimal("201.000"))).startsWith("deux cent un ");
        assertThat(this.service.spell(new BigDecimal("180.000"))).startsWith("cent quatre-vingts ");
    }

    @Test
    @DisplayName("mille never takes an s and is never preceded by un")
    void milleNeverTakesAnS() {
        assertThat(this.service.spell(new BigDecimal("1000.000"))).startsWith("mille ");
        assertThat(this.service.spell(new BigDecimal("2000.000"))).startsWith("deux mille ");
        assertThat(this.service.spell(new BigDecimal("21000.000"))).startsWith("vingt et un mille ");
    }

    @Test
    @DisplayName("million and milliard do take the plural")
    void millionAndMilliardTakeThePlural() {
        assertThat(this.service.spell(new BigDecimal("1000000.000"))).startsWith("un million ");
        assertThat(this.service.spell(new BigDecimal("2000000.000"))).startsWith("deux millions ");
        assertThat(this.service.spell(new BigDecimal("1000000000.000"))).startsWith("un milliard ");
    }

    @Test
    @DisplayName("a full contract amount reads as one sentence")
    void aFullContractAmountReadsAsOneSentence() {
        assertThat(this.service.spell(new BigDecimal("185025.750")))
                .isEqualTo("cent quatre-vingt-cinq mille vingt-cinq dinars et sept cent cinquante millimes");
    }

    @Test
    @DisplayName("a fourth decimal is rounded to the millime, like every other amount")
    void aFourthDecimalIsRoundedToTheMillime() {
        assertThat(this.service.spell(new BigDecimal("10.0005"))).isEqualTo("dix dinars et un millime");
    }
}

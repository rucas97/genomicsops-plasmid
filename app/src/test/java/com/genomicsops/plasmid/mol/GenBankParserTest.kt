package com.genomicsops.plasmid.mol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream

class GenBankParserTest {

    private val dummyGenBank = """
LOCUS       pUC19                  2686 bp    DNA     circular SYN 01-JAN-2024
FEATURES             Location/Qualifiers
     source          1..2686
                     /organism="synthetic DNA construct"
     gene            162..1013
                     /label="AmpR"
     CDS             162..1013
                     /label="AmpR"
ORIGIN
        1 gacgtcaggt ggcgactttt cggggaaatg tgcgcggaac ccctatttgt ttatttttct
       61 aaatacattc aaatatgtat ccgctcatga gacaataacc ctgataaatg cttcaataat
//
    """.trimIndent()

    @Test
    fun `parses plasmid name and length`() {
        val inputStream = ByteArrayInputStream(dummyGenBank.toByteArray())
        val plasmid = GenBankParser.parse(inputStream)
        
        assertNotNull(plasmid)
        assertEquals("pUC19", plasmid?.name)
        // Note: The dummy sequence above only has 120 bases for testing
        assertEquals(120, plasmid?.length) 
    }

    @Test
    fun `parses features`() {
        val inputStream = ByteArrayInputStream(dummyGenBank.toByteArray())
        val plasmid = GenBankParser.parse(inputStream)
        
        assertNotNull(plasmid)
        assertEquals(3, plasmid?.features?.size)
        
        val ampR = plasmid?.features?.find { it.name == "AmpR" }
        assertNotNull(ampR)
        assertEquals("CDS", ampR?.type)
        assertEquals(162, ampR?.start)
        assertEquals(1013, ampR?.end)
    }
}

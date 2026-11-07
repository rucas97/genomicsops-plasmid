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
        assertEquals(120, plasmid?.length) 
    }

    @Test
    fun `parses features correctly`() {
        val inputStream = ByteArrayInputStream(dummyGenBank.toByteArray())
        val plasmid = GenBankParser.parse(inputStream)
        
        assertNotNull(plasmid)
        // Should parse source, gene, and CDS
        assertEquals(3, plasmid?.features?.size)
        
        // Find the CDS feature specifically (both gene and CDS are named AmpR in the file)
        val cdsFeature = plasmid?.features?.find { it.type == "CDS" && it.name == "AmpR" }
        assertNotNull(cdsFeature)
        assertEquals(162, cdsFeature?.start)
        assertEquals(1013, cdsFeature?.end)
    }
}

/*
 * (c) 2015-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dateiformate fuer Speicherabbilddateien
 */

package jkcemu.file;

import jkcemu.lang.LangUtil;


public class FileFormat
{
  public static FileFormat BIN
	= new FileFormat( "file.filetype.bin_file_memory", 0 );

  public static FileFormat COM
	= new FileFormat( "file.filetype.com_file_cp", 0 );

  public static FileFormat BASIC_PRG
	= new FileFormat( "file.filetype.basic_program_file", 0 );

  public static FileFormat CDT
	= new FileFormat( "file.filetype.cpc_tape_file", 0 );

  public static FileFormat CSW
	= new FileFormat( "file.filetype.csw_file", 0 );

  public static FileFormat HEADERSAVE
	= new FileFormat( "file.filetype.headersave_file", 16 );

  public static FileFormat INTELHEX
	= new FileFormat( "file.filetype.intel_hex_file", 0 );

  public static FileFormat KCB
	= new FileFormat( "file.filetype.kcb_file_basic_program", 11 );

  public static FileFormat KCB_BLKN
	= new FileFormat( "file.filetype.kcb_file_basic_program_file_type_b", 11 );

  public static FileFormat KCB_BLKN_CKS
	= new FileFormat( "file.filetype.kcb_file_basic_program_file_type_b_p", 11 );

  public static FileFormat KCC
	= new FileFormat( "file.filetype.kcc_jtc_file", 11 );

  public static FileFormat KCC_BLKN
	= new FileFormat( "file.filetype.kcc_jtc_file_type_b", 11 );

  public static FileFormat KCC_BLKN_CKS
	= new FileFormat( "file.filetype.kcc_jtc_file_type_b_p", 11 );

  public static FileFormat KCTAP_SYS
	= new FileFormat( "file.filetype.kc_tap_file", 11 );

  public static FileFormat KCTAP_KC85
	= new FileFormat( "file.filetype.kc_tap_file_kc85", 11 );

  public static FileFormat KCTAP_Z9001
	= new FileFormat( "file.filetype.kc_tap_file_z9001", 11 );

  public static FileFormat KCTAP_BASIC_PRG
	= new FileFormat( "file.filetype.kc_tap_basic_program", 11 );

  public static FileFormat KCTAP_BASIC_DATA
	= new FileFormat( "file.filetype.kc_tap_basic_data", 8 );

  public static FileFormat KCTAP_BASIC_ASC
	= new FileFormat( "file.filetype.kc_tap_basic_ascii", 8 );

  public static FileFormat KCBASIC_HEAD_PRG
	= new FileFormat( "file.filetype.kc_basic_program_file_file_type_k", 8 );

  public static FileFormat KCBASIC_HEAD_PRG_BLKN
	= new FileFormat( "file.filetype.kc_basic_program_file_file_type_k_b", 8 );

  public static FileFormat KCBASIC_HEAD_PRG_BLKN_CKS
	= new FileFormat( "file.filetype.kc_basic_program_file_file_type_k_b_p", 8 );

  public static FileFormat KCBASIC_HEAD_DATA
	= new FileFormat( "file.filetype.kc_basic_data_field_file_type_k", 8 );

  public static FileFormat KCBASIC_HEAD_DATA_BLKN
	= new FileFormat( "file.filetype.kc_basic_data_field_file_type_k_b", 8 );

  public static FileFormat KCBASIC_HEAD_DATA_BLKN_CKS
	= new FileFormat( "file.filetype.kc_basic_data_field_file_type_k_b_p", 8 );

  public static FileFormat KCBASIC_HEAD_ASC
	= new FileFormat( "file.filetype.kc_basic_ascii_listing_file_type_k", 8 );

  public static FileFormat KCBASIC_HEAD_ASC_BLKN
	= new FileFormat( "file.filetype.kc_basic_ascii_listing_file_type_k_b", 8 );

  public static FileFormat KCBASIC_HEAD_ASC_BLKN_CKS
	= new FileFormat( "file.filetype.kc_basic_ascii_listing_file_type_k_b_p", 8 );

  public static FileFormat KCBASIC_PRG
	= new FileFormat( "file.filetype.kc_basic_program_file", 0 );

  public static FileFormat RBASIC_PRG
	= new FileFormat( "file.filetype.rbasic_program_file", 0 );

  public static FileFormat RMC
	= new FileFormat( "file.filetype.rbasic_machine_code_file", 0 );

  public static FileFormat TZX
	= new FileFormat( "file.filetype.zx_spectrum_tape", 0 );

  public static FileFormat ZXTAP
	= new FileFormat( "file.filetype.zx_tap_file", 0 );


  private String text;
  private int    maxFileDescLen;


  public int getMaxFileDescLength()
  {
    return this.maxFileDescLen;
  }


  public static int getTotalMaxFileDescLength()
  {
    return 16;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  /*
   * Die Bezeichnung wird in Auswahlfeldern angezeigt
   * und deshalb uebersetzt zurueckgeliefert.
   * Die Objekte selbst werden ueber die Identitaet verglichen,
   * so dass die Uebersetzung keine Auswertung beeinflusst.
   */
  public String toString()
  {
    return this.text != null ? LangUtil.getText( this.text ) : "";
  }


	/* --- Konstruktor --- */

  private FileFormat( String text, int maxFileDescLen )
  {
    this.text           = text;
    this.maxFileDescLen = maxFileDescLen;
  }
}

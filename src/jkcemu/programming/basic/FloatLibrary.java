/*
 * (c) 2025-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * BASIC-Bibliothek fuer Funktionen mit dem Datentyp FLOAT/SINGLE
 *
 * Format einer Float4-Zahl:
 *  Bit 0-22:  Mantisse
 *  Bit 23:    Vorzeichen
 *  Bit 24-31: Exponent mit Offset 127
 *
 * Unterschiede zum Standard IEEE-754:
 *  - Vorzeichen und Exponents sind vertauscht,
 *    damit man den Exponenten ohne eine 1-Bit-Verschiebung
 *    direkt in ein 8-Bit-Register laden kann.
 *  - NaN und Infinity werden nicht unterstuetzt.
 *    Ueberlaufe und andere Situationen,
 *    in denen keine gueltige Zahl entstehet,
 *    fuehren zu einem Fehler mit Programmabbruch.
 *  - Ist der Exponent 0, hat die Zahl den Wert 0,
 *    unabhaengig von der Mantisse.
 *  - Aufgrund der Little-Endian-Kodierung steht im Speicher
 *    das niederwertigste Byte der Mantisse an erster Stelle
 *    und der Exponent an letzter Stelle.
 *    Das Vorzeichen ist Bit 7 des vorletzten Bytes.
 */

package jkcemu.programming.basic;


public class FloatLibrary
{
  public static final int F4_BIAS = 0x7F;

  // jeweilige Anzahl der Koeffizienten ohne erstes Glied
  private static final int ATN_COEFF_CNT = 8;
  private static final int EXP_COEFF_CNT = 6;
  private static final int LN_COEFF_CNT  = 2;
  private static final int SIN_COEFF_CNT = 5;

  private static final float LN2 = (float) Math.log( 2F );


  public static void append_LD_DEHL_HALFPI( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, (float) (Math.PI * 0.5) );
  }


  public static void append_LD_DEHL_PI( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, (float) Math.PI );
  }


  public static void append_LD_DEHL_DOUBLEPI( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, (float) (Math.PI * 2.0) );
  }


  public static void append_LD_DEHL_EULER( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, (float) Math.E );
  }


  public static void appendCodeTo( BasicCompiler compiler )
  {
    AsmCodeBuf buf = compiler.getCodeBuf();

    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_ATN_F4 ) ) {
      /*
       * Berechnung des Arcustangens
       *
       * Abhaengig von x sind verschiedene Rechenschritte notwendig:
       *
       *   Fuer 0 <= x <= 0.5 wird arctan mit folgender Reihe berechnet:
       *     arctan( x ) = x - (x^3)/3 + (x^5)/5 - (x^7)/7 + (x^9)/9 + ...
       *
       *   Fuer 0.5 < x <= 1.0 wird x auf den Bereich 0 bis 0.5
       *   reduziert und dann mit der Reiche berechnet:
       *     arctan( x ) = arctan( x / (1F + sqr( 1 + (x^2) )) )
       *
       *   Fuer x > 1 wird x auf den Bereich 0 bis 1 reduziert
       *   und dann mit der vorherigen Logik berechnet:
       *     arctan( x ) = PI/2 - arctan( 1/x )
       *
       *   Fuer x < 0 wird die vorherige Berechnungslogik
       *   folgendermassen angewandt:
       *     arctan( x ) = -arctan( -x )
       */
      buf.append( "F_F4_ATN_F4:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"
		+ "\tBIT\t7,E\n"
		+ "\tJR\tZ,F_F4_ATN_F4_1\n"
      // x < 0
		+ "\tCALL\tF4_NEG_F4\n"
		+ "\tCALL\tF_F4_ATN_F4_1\n"
		+ "\tJP\tF4_NEG_F4\n"
      // x >= 0
		+ "F_F4_ATN_F4_1:\n"
		+ "\tLD\tBC,C_F4_1+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_ATN_F4_2\n"
      // x > 1
		+ "\tCALL\tF4_DIV_1_F4\n"
		+ "\tCALL\tF_F4_ATN_F4_2\n"
		+ "\tCALL\tF4_NEG_F4\n"
		+ "\tEXX\n" );
      append_LD_DEHL_HALFPI( buf );
      buf.append( "\tJP\tF4_ADD_F4_F4\n"
      // x <= 1
		+ "F_F4_ATN_F4_2:\n"
		+ "\tLD\tBC,C_F4_0_5+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_ATN_F4_3\n"
		+ "\tPUSH\tDE\n"		// x sichern
		+ "\tPUSH\tHL\n"
		+ "\tCALL\tF4_SQUARE_F4\n"	// x * x
		+ "\tCALL\tF4_ADD_F4_1\n"
		+ "\tCALL\tF_F4_SQR_F4\n"
		+ "\tCALL\tF4_ADD_F4_1\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_DIV_F4_F4\n"
		+ "\tCALL\tF_F4_ATN_F4_3\n"
		+ "\tJP\tF4_MUL_F4_2\n"
		+ "F_F4_ATN_F4_3:\n"
      // x <= 0.5
		+ "\tLD\tBC,TAB_ATN_COEFF\n"
		+ "\tJP\tF4_CALC_SQUARE_SERIES\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_SQR_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMPU_MEM_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CALC_SQUARE_SERIES );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_1_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_2 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NEG_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SQUARE_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_0_5 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_OP1_4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_POW_F4_F4 ) ) {
      /*
       * Implementierung der Power-Funktion a^b
       *
       * Fuer die Berechnung wird die Exponentialfunktion verwendet:
       *   a^b = e^(b*ln(a))
       *
       * Parameter:
       *   DEHL:     Basis als Float4-Wert
       *   D'E'H'L': Exponent als Float4-Wert
       * Rueckgabe:
       *   DEHL: Float4-Wert a^b
       */
      buf.append( "F_F4_POW_F4_F4:\n"
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"		// Exponent 0 ?
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,F_F4_POW_F3_F4_1\n" );
      append_LD_DEHL_1F( buf );
      buf.append( "\tRET\n"
		+ "F_F4_POW_F3_F4_1:\n"
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"		// Basis 0 oder kleiner 0 ?
		+ "\tOR\tA\n"
		+ "\tJP\tZ,LD_DEHL_NULL\n"
		+ "\tBIT\t7,E\n"
		+ "\tJR\tZ,F_F4_POW_F3_F4_2\n"
      /*
       * negative Basis -> Exponent pruefen
       *   Exponent negativ oder nicht genzzahlig    -> Fehler
       *   Exponent positiv, ganzzahlig und gerade   -> Ergebnis positiv
       *   Exponent positiv, genzzahlig und ungerade -> Ergebnis negativ
       */
		+ "\tEXX\n"
		+ "\tBIT\t7,E\n"
		+ "\tJP\tNZ,E_INVALID_PARAM\n"
		+ "\tPUSH\tDE\n"	// Exponent
		+ "\tPUSH\tHL\n"
		+ "\tCALL\tF4_TO_UINT\n"
		+ "\tLD\tA,B\n"		// Exponent ganzzahlig?
		+ "\tOR\tA\n"
		+ "\tJP\tNZ,E_INVALID_PARAM\n"
		+ "\tLD\tA,L\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
      // A0: Vorzeichen des Ergebnisses -> nach A7 schieben und sichern
		+ "\tRRA\n"
		+ "\tRRA\n"
		+ "\tPUSH\tAF\n"
      // Ergebnis vorzeichenlos berechnen
		+ "\tEXX\n"
		+ "\tRES\t7,E\n"	// positives Vorzeichen
		+ "\tCALL\tF_F4_POW_F3_F4_2\n"
      // Vorzeichen setzen
		+ "\tPOP\tAF\n"
		+ "\tAND\t80H\n"
		+ "\tOR\tE\n"
		+ "\tLD\tE,A\n"
		+ "\tRET\n"
      // eigentliche Berechnung
		+ "F_F4_POW_F3_F4_2:\n"
      // Exponent sichern
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
      // natuerlichen Logarithmus der Basis ermitteln
		+ "\tCALL\tF_F4_LN_F4\n"
      // mit Exponenten multiplizieren
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tCALL\tF4_MUL_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_EXP_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_LN_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
      compiler.addLibItem( BasicLibrary.LibItem.E_INVALID_PARAM );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      // direkt weiter mit F_F4_EXP_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_EXP_F4 ) ) {
      /*
       * Implementierung der Exponentialfunktion zur Basis E
       *
       * Die Exponentialfunktion wird so zerlegt, dass die eigentliche
       * Berechnung mit Hilfe der Taylorreihe mit einem Wert kleiner 1
       * erfolgen kann, da nur in diesem Bereich die Reihe
       * ausreichend schnell konvergiert.
       *
       * e^x = e^((k*ln(2))+r) = e^(k*ln(2)) * e^r = 2^k * e^r
       *   x = (k*ln(2)) + r
       *   r = x - (k*ln(2))
       */
      buf.append( "F_F4_EXP_F4:\n"
		+ "\tPUSH\tDE\n"		// x
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, LN2 );
      buf.append( "\tCALL\tF4_DIV_F4_F4\n"
		+ "\tPUSH\tDE\n"		// Vorzeichen sichern
		+ "\tCALL\tF4_TO_UINT\n"
		+ "\tPOP\tBC\n"			// C7: Vorzeichen
		+ "\tLD\tA,E\n"
		+ "\tOR\tH\n"
		+ "\tJR\tZ,F4_DIV_F4_F4_1\n"
      // Betrag von k zu gross
		+ "\tPOP\tBC\n"			// Stack aufraeumen
		+ "\tPOP\tBC\n"
		+ "\tBIT\t7,C\n"
		+ "\tJP\tZ,E_NUMERIC_OVERFLOW\n"
		+ "\tJP\tLD_DEHL_NULL\n"
		+ "F4_DIV_F4_F4_1:\n"
		+ "\tLD\tA," );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tBIT\t7,C\n"
		+ "\tJR\tNZ,F_F4_EXP_F4_2\n"
		+ "\tADD\tA,L\n"		// Exponent fuer 2^k
		+ "\tJP\tC,E_NUMERIC_OVERFLOW\n"
		+ "\tCP\t0FFH\n"
		+ "\tJP\tZ,E_NUMERIC_OVERFLOW\n"
		+ "\tPUSH\tAF\n"		// Exponent fuer 2^k
		+ "\tJR\tF_F4_EXP_F4_4\n"
		+ "F_F4_EXP_F4_2:\n"
		+ "\tSUB\tL\n"			// Exponent fuer 2^k
		+ "\tJR\tNC,F_F4_EXP_F4_3\n"
		+ "\tPOP\tBC\n"			// Stack aufraeumen
		+ "\tPOP\tBC\n"
		+ "\tJP\tLD_DEHL_NULL\n"
		+ "F_F4_EXP_F4_3:\n"
		+ "\tPUSH\tAF\n"		// Exponent fuer 2^k
		+ "\tCALL\tI2_NEG_HL\n"
		+ "F_F4_EXP_F4_4:\n"
		+ "\tCALL\tF_F4_CSNG_I2\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, LN2 );
      buf.append( "\tCALL\tF4_MUL_F4_F4\n"
		+ "\tEXX\n"
		+ "\tPOP\tAF\n"			// Exponent fuer 2^k
		+ "\tPOP\tHL\n"			// x
		+ "\tPOP\tDE\n"
		+ "\tPUSH\tAF\n"		// Exponent fuer 2^k
		+ "\tEXX\n"
		+ "\tCALL\tF4_SUB_F4_F4\n"	// Ergebnis: r
      /*
       * Taylorreihe 1 + x + x^2/2! + x^3/3! + ...
       * mit x = r berechnen
       */
		+ "\tLD\t(M_ACCU),HL\n"
		+ "\tLD\t(M_ACCU+2),DE\n"
		+ "\tLD\t(M_OP1),HL\n"
		+ "\tLD\t(M_OP1+2),DE\n"
		+ "\tCALL\tF4_ADD_F4_1\n"	// Startwert
		+ "\tLD\tBC,TAB_EXP_COEFF\n"
		+ "\tCALL\tF4_CALC_SERIES\n"
      // mit 2^k multiplizieren -> Exponent mit k addieren
		+ "\tPOP\tAF\n"
		+ "\tCALL\tF4_ADD_EXP\n"
		+ "\tRET\tNC\n"
		+ "\tJP\tE_NUMERIC_OVERFLOW\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_CSNG_I2 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_EXP );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CALC_SERIES );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
      compiler.addLibItem( BasicLibrary.LibItem.I2_NEG_HL );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_LN_F4 ) ) {
      /*
       * Berechnung des natuerlichen Logarithmus
       *
       * Im ersten Schritt wird x in den Bereich 0.5 bis 1 gebracht
       * und eine Differenz berechnet (n * ln(2)),
       * die dann spaeter zu addieren ist:
       *   ln( x * 0.5 ) = ln(x ) + ln( 0.5 )
       *   ln( x * 2 )   = ln(x ) + ln( 2 )
       *
       * Da ln( 0.5 ) = -ln( 2 ) ist, kann der erste Schritt ohne
       * Unterscheidung zwischen kleiner 0.5 und groesser 1 erfolgen.
       *
       * Die weiter unten verwendete Taylorreihe konvergiert
       * bei x kleiner 0.75 nicht schnell genug.
       * Aus diesem Grund wird in dem Fall x mit 1.5 multipliziert
       * und die o.g. Differenz um ln(1.5) reduziert.
       *
       * Im dritten Schritt wird der Logarithmus mit folgender Reihe
       * berechnet und der Wert aus den ersten beiden Schritten addiert:
       *   ln( x ) = ln( (1-a) / (1+a) )
       *           = 2a + 2/3a^3 + 2/5a^5 + ...
       *           = 2 * (a + (a^3)/3 + (a^5)/5 + ...)
       *
       * Im Bereich von 0.9997 <= x <= 1.0007 liefrt die lineare
       * Interpolation y = x - 1 genauere Ergebisse
       * als der oeben beschriebene Rechenweg.
       */
      buf.append( "F_F4_LN_F4:\n"
      // x < 0 ?
		+ "\tBIT\t7,E\n"
		+ "\tJP\tNZ,E_INVALID_PARAM\n"
      /*
       * bei 0.9997 <= x <= 1.0007 lineare Interpolation
       * y = x - 1 verwenden
       */
		+ "\tLD\tBC,C_F4_0_9997+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_LN_F4_1\n"
		+ "\tLD\tBC,C_F4_1_0007+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJP\tNC,F4_SUB_F4_1\n"
      // x == 0 ?
		+ "F_F4_LN_F4_1:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJP\tZ,E_INVALID_PARAM\n"
      // x in den Bereich 0.5 bis 1 schieben und Offset berechnen
		+ "\tLD\tD,7EH\n"
		+ "\tPUSH\tDE\n"		// neues x sichern
		+ "\tPUSH\tHL\n"
		+ "\tSUB\tD\n"
		+ "\tLD\tL,A\n"
		+ "\tLD\tH,00H\n"
		+ "\tJP\tP,F_F4_LN_F4_2\n"
		+ "\tDEC\tH\n"
		+ "F_F4_LN_F4_2:\n"
		+ "\tCALL\tF_F4_CSNG_I2\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, LN2 );
      buf.append( "\tCALL\tF4_MUL_F4_F4\n"
		+ "\tEXX\n"
      // neues X holen und Differenz sichern
		+ "\tPOP\tHL\n"			// x
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"		// Differenz
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
      // bei x >= 0.9997 lineare Interpolation y = x - 1 verwenden
		+ "\tLD\tBC,C_F4_0_9997+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_LN_F4_3\n"
		+ "\tCALL\tF4_SUB_F4_1\n"
		+ "\tJR\tF_F4_LN_F4_5\n"
      /*
       * Wenn x < 0.75, dann x mit 1.5 multiplizieren
       * und die Differenz um ln(1.5) reduzieren
       */
		+ "F_F4_LN_F4_3:\n"

		+ "\tLD\tBC,C_F4_0_75+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tC,F_F4_LN_F4_4\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, 1.5F );
      buf.append( "\tCALL\tF4_MULU_F4_F4\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"			// Differenz
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"		// x
		+ "\tPUSH\tHL\n" );
      append_LD_DEHL_F4( buf, (float) -Math.log( 1.5 ) );
      buf.append( "\tCALL\tF4_ADD_F4_F4\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"			// x
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"		// Differenz
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
      /*
       * aus neuem x (0.75 bis 1.0) Logarithmus berechnen,
       * zuerst: a = (x - 1) / (x + 1)
       */
		+ "F_F4_LN_F4_4:\n"
		+ "\tPUSH\tDE\n"		// x sichern
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n" );
      append_LD_DEHL_1F( buf );
      buf.append( "\tCALL\tF4_SUB_F4_F4\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"			// x wiederherstellen
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"		// x - 1
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_ADD_F4_1\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"			// x - 1
		+ "\tPOP\tDE\n"
		+ "\tEXX\n"			// x + 1
		+ "\tCALL\tF4_DIV_F4_F4\n"
		+ "\tLD\tBC,TAB_LN_COEFF\n"
		+ "\tCALL\tF4_CALC_SQUARE_SERIES\n"
      // y = y + y
		+ "\tPUSH\tDE\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tCALL\tF4_ADD_F4_F4\n"
      // Wert aus erstem Rechenschritt addieren
		+ "F_F4_LN_F4_5:\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tJP\tF4_ADD_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_CSNG_I2 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CALC_SQUARE_SERIES );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMPU_MEM_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.E_INVALID_PARAM );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_0_75 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_0_9997 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_1 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_1_0007 );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_OP1_4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_TAN_F4 ) ) {
      /*
       * Berechnung des Tangens mittels folgendem Algorithmus:
       *   tan( x ) = sin( x ) / cos( x )
       *
       * Parameter:
       *   DEHL: Float4-Wert des Winkels im Bogenmass
       * Rueckgabe:
       *   DEHL: Float4-Wert des Tangens
       */
      buf.append( "F_F4_TAN_F4:\n"
		+ "\tCALL\tF4_NORM_RADIAN\n"
		+ "\tPUSH\tHL\n"		// X retten
		+ "\tPUSH\tDE\n"
		+ "\tCALL\tF_F4_COS_F4\n"	// cos(x) berechnen
		+ "\tEXX\n"
		+ "\tPOP\tDE\n"			// x wiederherstellen
		+ "\tPOP\tHL\n"
		+ "\tEXX\n"
		+ "\tPUSH\tHL\n"		// Cosinus retten
		+ "\tPUSH\tDE\n"
		+ "\tEXX\n"
		+ "\tCALL\tF_F4_SIN_F4_1\n"	// sin(x) berechnen
		+ "\tEXX\n"
		+ "\tPOP\tDE\n"			// Sinus wiederherstellen
		+ "\tPOP\tHL\n"
		+ "\tJP\tF4_DIV_F4_F4\n" );	// sin(x)/cos(x) berechnen
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORM_RADIAN );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_SIN_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_COS_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_COS_F4 ) ) {
      /*
       * Berechnung des Cosinus mittels folgendem Algorithmus:
       *   cos( x ) = sin( x + PI/2 )
       *
       * Parameter:
       *   DEHL: Float4-Wert des Winkels im Bogenmass
       * Rueckgabe:
       *   DEHL: Float4-Wert des Cosinus
       */
      buf.append( "F_F4_COS_F4:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_HALFPI( buf );
      buf.append( "\tCALL\tF4_ADD_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_SIN_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      // direkt weiter mit F_F4_SIN_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_SIN_F4 ) ) {
      /*
       * Berechnung des Sinus mit folgender Reihe:
       *   sin( x ) = x - (x^3)/3! + (x^5)/5! - (x^7)/7! + (x^9)/9! + ...
       *
       * Parameter:
       *   DEHL: Float4-Wert des Winkels im Bogenmass
       * Rueckgabe:
       *   DEHL: Float4-Wert des Sinus
       */
      buf.append( "F_F4_SIN_F4:\n"
		+ "\tCALL\tF4_NORM_RADIAN\n"
		+ "F_F4_SIN_F4_1:\n"
		+ "\tLD\tBC,C_F4_HALFPI+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_SIN_F4_5\n"	// erster Quadrant
		+ "\tLD\tBC,C_F4_PI+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_SIN_F4_3\n"	// zweiter Quadrant
		+ "\tLD\tBC,C_F4_15PI+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tJR\tNC,F_F4_SIN_F4_2\n"	// dritter Quadrant
      // vierter Quadrant
		+ "\tEXX\n"
		+ "\tLD\tBC,C_F4_DOUBLEPI\n"
		+ "\tCALL\tF_F4_SIN_F4_4\n"
		+ "\tSET\t7,E\n"
		+ "\tRET\n"
      // dritter Quadrant
		+ "F_F4_SIN_F4_2:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_PI( buf );
      buf.append( "\tCALL\tF4_SUB_F4_F4\n"
		+ "\tCALL\tF_F4_SIN_F4_5\n"
		+ "\tSET\t7,E\n"
		+ "\tRET\n"
      // zweiter Quadrant
		+ "F_F4_SIN_F4_3:\n"
		+ "\tEXX\n"
		+ "\tLD\tBC,C_F4_PI\n"
      // Sinus-Berechnung mit Offset innerhalb des Normalbereichs
		+ "F_F4_SIN_F4_4:\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tLD\tL,A\n"
		+ "\tINC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tLD\tH,A\n"
		+ "\tINC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tLD\tE,A\n"
		+ "\tINC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tLD\tD,A\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_SUB_F4_F4\n"
      // erster Quadrant
		+ "F_F4_SIN_F4_5:\n"
		+ "\tLD\tBC,TAB_SIN_COEFF\n"
		+ "\tJR\tF4_CALC_SQUARE_SERIES\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORM_RADIAN );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CALC_SQUARE_SERIES );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMPU_MEM_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_HALFPI );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_PI );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_15PI );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_DOUBLEPI );
    }
    if( compiler.usesLibItem(
			BasicLibrary.LibItem.F4_CALC_SQUARE_SERIES ) )
   {
      /*
       * Berechnung einer Reihe mit einem Multiplikator,
       * der dem Quadrat des Startwerts entspricht.
       *
       * Vor der Iteration wird M_OP1 = M_ACCU * M_ACCU gesetzt.
       *
       * Pro Iterationsschritt wird berechnet:
       *   M_ACCU = M_ACCU * M_OP1
       *   DEHL   = DEHL + (M_ACCU * (BC))
       *   BC     = BC + 2
       *
       * Parameter:
       *   BC:       Zeiger auf die Koeffiziententabelle,
       *             Das erste Byte enthaelt die Anzahl der Koeffizienten.
       *   DEHL:     Startwert
       */
      buf.append( "F4_CALC_SQUARE_SERIES:\n"
		+ "\tPUSH\tBC\n"
		+ "\tLD\t(M_ACCU),HL\n"
		+ "\tLD\t(M_ACCU+2),DE\n"
		+ "\tPUSH\tDE\n"		// Startwert sichern
		+ "\tPUSH\tHL\n"
		+ "\tCALL\tF4_SQUARE_F4\n"	// Startwert quadrieren
		+ "\tLD\t(M_OP1),HL\n"
		+ "\tLD\t(M_OP1+2),DE\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tPOP\tBC\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CALC_SERIES );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SQUARE_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_OP1_4 );
      // direkt weiter mit F4_CALC_SERIES
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_CALC_SERIES ) ) {
      /*
       * Berechnung einer Reihe
       *
       * Pro Iterationsschritt wird berechnet:
       *   M_ACCU = M_ACCU * M_OP1
       *   DEHL   = DEHL + (M_ACCU * (BC))
       *   BC     = BC + 2
       *
       * Parameter:
       *   BC:       Zeiger auf die Koeffiziententabelle,
       *             Das erste Byte enthaelt die Anzahl der Koeffizienten.
       *   DEHL:     Startwert
       *   (M_OP1):  Multiplikator
       */
      buf.append( "F4_CALC_SERIES:\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tINC\tBC\n"
		+ "\tLD\t(M_TABPTR),BC\n"
		+ "F4_CALC_SERIES_1:\n"
		+ "\tPUSH\tAF\n"		// Anzahl Koeffizienten
		+ "\tPUSH\tDE\n"		// X sichern
		+ "\tPUSH\tHL\n"
      // M_ACCU = M_ACCU * M_OP1
		+ "\tLD\tHL,(M_ACCU)\n"
		+ "\tLD\tDE,(M_ACCU+2)\n"
		+ "\tEXX\n"
		+ "\tLD\tHL,(M_OP1)\n"
		+ "\tLD\tDE,(M_OP1+2)\n"
		+ "\tCALL\tF4_MUL_F4_F4\n"
		+ "\tLD\t(M_ACCU),HL\n"
		+ "\tLD\t(M_ACCU+2),DE\n"
      // DEHL = M_ACCU * Koeffizient
		+ "\tEXX\n"
		+ "\tLD\tHL,(M_TABPTR)\n"
		+ "\tLD\tC,(HL)\n"
		+ "\tINC\tHL\n"
		+ "\tLD\tB,(HL)\n"
		+ "\tINC\tHL\n"
		+ "\tLD\tE,(HL)\n"
		+ "\tINC\tHL\n"
		+ "\tLD\tD,(HL)\n"
		+ "\tINC\tHL\n"
		+ "\tLD\t(M_TABPTR),HL\n"
		+ "\tLD\tH,B\n"
		+ "\tLD\tL,C\n"
		+ "\tCALL\tF4_MUL_F4_F4\n"
      // X = X + DEHL
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tCALL\tF4_ADD_F4_F4\n"
      // noch ein Durchlauf?
		+ "\tPOP\tAF\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,F4_CALC_SERIES_1\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_OP1_4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_NORM_RADIAN ) ) {
      /*
       * Die Methode verschiebt den uebergebenen Wert im Bogenmass
       * in den Normalbereich 0 <= x < 2*PI.
       *
       * Parameter:
       *   DEHL: Bogenmass als Float4-Wert
       * Rueckgabe:
       *   DEHL: Bogenmass im Normalbereich als Float4-Wert
       */
      buf.append( "F4_NORM_RADIAN:\n"
		+ "\tBIT\t7,E\n"
		+ "\tJR\tNZ,F4_NORM_RADIAN_1\n"
		+ "\tLD\tBC,C_F4_DOUBLEPI+3\n"
		+ "\tCALL\tF4_CMPU_MEM_F4\n"
		+ "\tRET\tNC\n"
		+ "F4_NORM_RADIAN_1:\n"
      // Wert liegt ausserhalb des Normalbereichs
		+ "\tPUSH\tHL\n"
		+ "\tPUSH\tDE\n"
      // durch 2*PI dividieren
		+ "\tEXX\n" );
      append_LD_DEHL_DOUBLEPI( buf );
      buf.append( "\tCALL\tF4_DIV_F4_F4\n"
      // ganzahligen Anteil ermitteln
		+ "\tCALL\tF_F4_INT_F4\n"
      // mit 2*PI multiplizieren
		+ "\tEXX\n" );
      append_LD_DEHL_DOUBLEPI( buf );
      buf.append( "\tCALL\tF4_MUL_F4_F4\n"
      // vom uebergebenen Wert subtrahieren
		+ "\tEXX\n"
		+ "\tPOP\tDE\n"
		+ "\tPOP\tHL\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_SUB_F4_F4\n"
		+ "\tBIT\t7,E\n"
		+ "\tRET\tZ\n"
      /*
       * Sollte durch Rundungsfehler ein negativer Wert entstanden sein,
       * dann diesen auf 0 setzen
       */
		+ "\tLD\tD,00H\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_INT_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMPU_MEM_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.C_F4_DOUBLEPI );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_SQR_F4 ) ) {
      /*
       * Berechnung der Quadratwurzel mittels Heron:
       *   x(n+1) = (x(n) + (a / x(n))) / 2
       *
       * Parameter:
       *   DEHL: Float4-Eingangswert
       * Rueckgabe:
       *   DEHL: Float4-Quadratwurzel
       */
      buf.append( "F_F4_SQR_F4:\n"
		+ "\tBIT\t7,E\n"
		+ "\tJP\tNZ,E_INVALID_PARAM\n"
      // Test auf Null
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"			// SQR(0)=0
		+ "\tLD\t(M_OP1),HL\n"
		+ "\tLD\t(M_OP1+2),DE\n"
      // Startwert
		+ "\tLD\tA,D\n"
		+ "\tINC\tA\n"
		+ "\tJR\tNZ,F_F4_SQR_F4_1\n"
		+ "\tLD\tD,0BFH\n"
		+ "\tJR\tF_F4_SQR_F4_2\n"
		+ "F_F4_SQR_F4_1:\n"
		+ "\tSUB\t80H\n"
		+ "\tSRA\tA\n"
		+ "\tADD\tA," );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tLD\tD,A\n"
      /*
       * Iterationsschleife,
       * aktuellen Wert in M_ACCU merken
       */
		+ "F_F4_SQR_F4_2:\n"
		+ "\tLD\tB,05H\n"
		+ "F_F4_SQR_F4_3:\n"
		+ "\tLD\t(M_ACCU),HL\n"
		+ "\tLD\t(M_ACCU+2),DE\n"
		+ "\tPUSH\tBC\n"
		+ "\tPUSH\tDE\n"
		+ "\tPUSH\tHL\n"
      // Division a / x(n)
		+ "\tEXX\n"
		+ "\tLD\tHL,(M_OP1)\n"
		+ "\tLD\tDE,(M_OP1+2)\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_DIVU_F4_F4\n"
		+ "\tRES\t7,E\n"
      // Addition mit x(x)
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tCALL\tF4_ADD_F4_F4\n"
      // Division durch 2
		+ "\tDEC\tD\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tZ\n"			// Ergebnis 0
		+ "\tDJNZ\tF_F4_SQR_F4_3\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.E_INVALID_PARAM );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_4 );
      compiler.addLibItem( BasicLibrary.LibItem.M_OP1_4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_RND ) ) {
      /*
       * Ermittlung einer Zufallszahl im Bereich 0 < x < 1
       *
       * Rueckgabewert:
       *   DEHL:  Zufallszahl im Bereich 0 < x < 1
       */
      buf.append( "F_F4_RND:\n"
		+ "\tCALL\tI2_RND\n"		
		+ "\tPUSH\tHL\n"
		+ "\tCALL\tI2_RND\n"		
		+ "\tPOP\tDE\n"
		+ "\tRES\t7,E\n"
		+ "\tLD\tA,D\n"
		+ "\tAND\t03H\n"
		+ "\tADD\tA,7BH\n"
		+ "\tLD\tD,A\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.I2_RND );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_INT_F4 ) ) {
      /*
       * Abrunden auf die naechstkleinere ganzen Zahl
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL: ganze Zahl als Float4-Wert
       */
      buf.append( "F_F4_INT_F4:\n"
		+ "\tCALL\tF_F4_FIX_F4\n"
		+ "\tLD\tA,E\n"
		+ "\tOR\tA\n"
		+ "\tRET\tP\n"
      // testen, ob Bits herausgeschoben wurden (Nachkommaanteil)
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"		// wenn kein Nachkommaanteil
      // 1 subtrahieren
		+ "\tJP\tF4_SUB_F4_1\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_FIX_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_SUB_F4_1 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_ROUND_F4 ) ) {
      /*
       * Runden auf eine ganze Zahl
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL: ganze Zahl als Float4-Wert
       */
      buf.append( "F_F4_ROUND_F4:\n"
		+ "\tLD\tA,E\n"
		+ "\tAND\t80H\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, 0.5F );
      buf.append( "\tOR\tE\n"
		+ "\tLD\tE,A\n"			// gleiches Vorzeichen
		+ "\tCALL\tF4_ADD_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F_F4_FIX_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      // direkt weiter mit F_F4_FIX_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_FIX_F4 ) ) {
      /*
       * Ermitteln des Integer-Anteils eines Float4-Wertes
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL: ganze Zahl als Float4-Wert
       *   B=0:  uebergebener Wert war bereits ganzzahlig
       */
      buf.append( "F_F4_FIX_F4:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"		// Null
      // bereits Ganzzahl?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tRET\tNC\n"		// ja
		+ "\tLD\tA,E\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tLD\tA,D\n"
      // kleiner 1?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJR\tNC,F_F4_INT_F4_1\n"
		+ "\tLD\tD,00H\n"
		+ "\tJR\tF_F4_INT_F4_2\n"
		+ "F_F4_INT_F4_1:\n"
		+ "\tCALL\tF4_TO_UINT\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_NORMALIZE\n"
		+ "\tPOP\tBC\n"
		+ "F_F4_INT_F4_2:\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tOR\tA\n"
		+ "\tRET\tP\n"
      // negatives Ergebnis
		+ "\tSET\t7,E\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_FRAC_F4 ) ) {
      /*
       * Ermitteln des Nachkommaanteils eines Float4-Wertes
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL: Nachkommaanteil als Float4-Wert
       */
      buf.append( "F_F4_FRAC_F4:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"		// Null
		+ "\tRES\t7,E\n"
		+ "\tCP\t" );		// kleiner 1 ?
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tRET\tC\n"		// ja
		+ "\tCP\t" );		// ganze Zahl ?
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tJP\tNC,LD_DEHL_NULL\n"
      /*
       * Mantisse solange nach links schieben,
       * bis Exponent = Bias - 1 ist
       */
		+ "\tLD\tA,D\n"
		+ "F_F4_FRAC_F4_2:\n"
		+ "\tCP\t" );		// byteweise schieben?
      buf.appendHex2( F4_BIAS + 8 );
      buf.append( "\n"
		+ "\tJR\tC,F_F4_FRAC_F4_3\n"
		+ "\tLD\tE,H\n"
		+ "\tLD\tH,L\n"
		+ "\tLD\tL,00H\n"
		+ "\tSUB\t8\n"
		+ "\tJR\tF_F4_FRAC_F4_2\n"
      // bitweise schieben
		+ "F_F4_FRAC_F4_3:\n"
		+ "\tSLA\tL\n"
		+ "\tRL\tH\n"
		+ "\tRL\tE\n"
		+ "\tDEC\tA\n"
		+ "\tCP\t" );		// kleiner 1 ?
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJR\tNC,F_F4_FRAC_F4_3\n"
		+ "\tLD\tD,A\n"
      // Test auf 0 und normalisieren
		+ "\tLD\tA,E\n"
		+ "\tOR\tH\n"
		+ "\tOR\tL\n"
		+ "\tJP\tZ,LD_DEHL_NULL\n"
		+ "\tJP\tF4_NORMALIZE\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_D6_CDEC_F4 ) ) {
      /*
       * Umwandlung eines Float4-Wertes in einen Dec6-Wert.
       *
       * Parameter:
       *   DEHL:  Float4-Wert
       * Rueckgabewert:
       *   M_ACCU: Dec6-Wert
       */
      buf.append( "F_D6_CDEC_F4:\n"
		+ "\tCALL\tF4_TO_BCD\n"
		+ "\tXOR\tA\n"
		+ "\tSUB\tC\n"	// Dezimalexponent -> Anz. Nachkommastellen
		+ "\tLD\tBC,0000H\n"		// oberste Dec6-Stellen
		+ "\tJP\tM,F_D6_CDEC_F4_2\n"
		+ "\tCP\t0EH\n"
		+ "\tJR\tC,F_D6_CDEC_F4_1\n"
		+ "\tCALL\tLD_DEHL_NULL\n"
		+ "\tJR\tF_D6_CDEC_F4_7\n"
      /*
       * Solange die Anzahl der Nachkommastellen negativ ist,
       * muessen die Ziffern nach links geschoben werden.
       */
		+ "F_D6_CDEC_F4_1:\n"
		+ "\tOR\tA\n"
		+ "\tJP\tP,F_D6_CDEC_F4_4\n"
      // hoechstwertige Ziffer Null, um schieben zu koennen?
		+ "F_D6_CDEC_F4_2:\n"
		+ "\tEX\tAF,AF'\n"
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tJP\tNZ,E_NUMERIC_OVERFLOW\n"
		+ "\tLD\tA,04H\n"
		+ "F_D6_CDEC_F4_3:\n"
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tRL\tD\n"
		+ "\tRL\tC\n"
		+ "\tRL\tB\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,F_D6_CDEC_F4_3\n"
		+ "\tEX\tAF,AF'\n"
		+ "\tINC\tA\n"
		+ "\tJR\tZ,F_D6_CDEC_F4_6\n"
		+ "\tJR\tF_D6_CDEC_F4_1\n"
      /*
       * Solange die Anzahl der Nachkommastellen groesser 7 ist,
       * muessen die Dezimalziffern nach rechts geschoben werden.
       * BC braucht nicht geschoben zu werden,
       * da BC nur dann einen Wert ungleich Null haben kann,
       * wenn die Anzahl der Nachkommastellen urspruenglich negativ war.
       * In dem Fall wird aber dieser Block uebersprungen.
       */
		+ "F_D6_CDEC_F4_4:\n"
		+ "\tCP\t08H\n"
		+ "\tJR\tC,F_D6_CDEC_F4_6\n"
		+ "\tEX\tAF,AF'\n"
		+ "\tLD\tA,04H\n"
		+ "F_D6_CDEC_F4_5:\n"
		+ "\tSRL\tD\n"
		+ "\tRR\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,F_D6_CDEC_F4_5\n"
		+ "\tEX\tAF,AF'\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tF_D6_CDEC_F4_4\n"
      // Dec6-Wert vervollstaendigen
		+ "F_D6_CDEC_F4_6:\n"
		+ "\tSLA\tA\n"
		+ "\tSLA\tA\n"
		+ "\tSLA\tA\n"
		+ "\tSLA\tA\n"
		+ "\tOR\tB\n"
		+ "\tLD\tB,A\n"
		+ "\tLD\tA,(M_SIGN)\n"
		+ "\tAND\t80H\n"
		+ "\tOR\tB\n"
		+ "\tLD\tB,A\n"
		+ "F_D6_CDEC_F4_7:\n"
		+ "\tPUSH\tHL\n"
		+ "\tLD\tHL,M_ACCU\n"
		+ "\tLD\t(HL),B\n"
		+ "\tINC\tHL\n"
		+ "\tLD\t(HL),C\n"
		+ "\tINC\tHL\n"
		+ "\tLD\t(HL),D\n"
		+ "\tINC\tHL\n"
		+ "\tLD\t(HL),E\n"
		+ "\tINC\tHL\n"
		+ "\tPOP\tDE\n"
		+ "\tLD\t(HL),D\n"
		+ "\tINC\tHL\n"
		+ "\tLD\t(HL),E\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_BCD );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_6 );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_I2_CINT_F4 ) ) {
      /*
       * Umwandlung eines Float4-Wertes in einen Integer-Wert
       *
       * Parameter:
       *   DEHL:  Float4-Wert
       * Rueckgabewert:
       *   HL:    Integer-Wert
       */
      buf.append( "F_I2_CINT_F4:\n"
		+ "\tLD\tA,D\n"
      // kleiner 1?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJR\tC,F_I2_CINT_F4_1\n"	// ja -> Null
      // Zahl zu gross?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 15 );
      buf.append( "\n"
		+ "\tJP\tNC,E_NUMERIC_OVERFLOW\n"
		+ "\tLD\tA,E\n"
		+ "\tPUSH\tAF\n"		// Vorzeichen
		+ "\tCALL\tF4_TO_UINT\n"
		+ "\tLD\tA,E\n"
		+ "\tOR\tA\n"
		+ "\tJP\tNZ,E_NUMERIC_OVERFLOW\n"
		+ "\tPOP\tAF\n"			// Vorzeichen
		+ "\tOR\tA\n"
		+ "\tRET\tP\n"			// groesser Null
		+ "\tJP\tI2_NEG_HL\n"
		+ "F_I2_CINT_F4_1:\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
      compiler.addLibItem( BasicLibrary.LibItem.I2_NEG_HL );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_I4_CLNG_F4 ) ) {
      /*
       * Umwandlung eines Float4-Wertes in einen Long-Wert
       *
       * Parameter:
       *   DEHL:  Float4-Wert
       * Rueckgabewert:
       *   DEHL:  Long-Wert
       */
      buf.append( "F_I4_CLNG_F4:\n"
		+ "\tLD\tA,D\n"
      // kleiner 1?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJP\tC,LD_DEHL_NULL\n"	// ja -> Null
      // Zahl zu gross?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 15 );
      buf.append( "\n"
		+ "\tJP\tNC,E_NUMERIC_OVERFLOW\n"
		+ "\tLD\tA,E\n"
		+ "\tPUSH\tAF\n"		// Vorzeichen
		+ "\tSET\t7,E\n"
		+ "\tLD\tA,D\n"
      // kleiner 24 Bit?
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tJR\tC,F_I4_CLNG_F4_2\n"
      // Zahl groesser/gleich 24 Bit -> ggf. Mantisse links schieben
		+ "\tLD\tD,00H\n"
		+ "F_I4_CLNG_F4_1:\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tJR\tZ,F_I4_CLNG_F4_3\n"
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tRL\tD\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tF_I4_CLNG_F4_1\n"
      // Zahl kleiner 24 Bit
		+ "F_I4_CLNG_F4_2:\n"
		+ "\tCALL\tF4_TO_UINT\n"
		+ "\tLD\tD,00H\n"
		+ "F_I4_CLNG_F4_3:\n"
		+ "\tPOP\tAF\n"			// Vorzeichen
		+ "\tOR\tA\n"
		+ "\tRET\tP\n"			// groesser Null
		+ "\tJP\tI4_NEG_DEHL\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
      compiler.addLibItem( BasicLibrary.LibItem.I4_NEG_DEHL );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_VAL_S ) ) {
      /*
       * Lesen einer Float4-Zahl aus einer Zeichenkette,
       * Fehlervariablen werden gesetzt.
       *
       * Parameter:
       *   HL: Zeiger auf die Zeichenkette
       * Rueckgabe:
       *   DEHL: gelesene Zahl oder (M_ERROR_NUM) != 0 bei Fehler
       */
      buf.append( "F_F4_VAL_S:\n"
      // Leerzeichen uebergehen
		+ "\tEX\tDE,HL\n"		// DE: Lesezeiger
		+ "\tLD\tA,(DE)\n"
		+ "\tINC\tDE\n"
		+ "\tCP\t20H\n"
		+ "\tJR\tZ,F_F4_VAL_S\n"
      // Vorzeichen
		+ "\tLD\tB,00H\n"
		+ "\tCP\t2DH\n"			// Minuszeichen
		+ "\tJR\tNZ,F_F4_VAL_S_1\n"
		+ "\tLD\tB,80H\n"
		+ "\tJR\tF_F4_VAL_S_2\n"
		+ "F_F4_VAL_S_1:\n"
		+ "\tCP\t2BH\n"			// Pluszeichen
		+ "\tJR\tNZ,F_F4_VAL_S_3\n"
		+ "F_F4_VAL_S_2:\n"
		+ "\tLD\tA,(DE)\n"
		+ "\tINC\tDE\n"
		+ "F_F4_VAL_S_3:\n"
		+ "\tLD\tHL,M_SIGN\n"
		+ "\tLD\t(HL),B\n"
      // Test auf Ziffer oder Dezimalpunkt
		+ "\tCP\t2EH\n"			// Dezimalpunkt
		+ "\tJR\tZ,F_F4_VAL_S_4\n"
		+ "\tCP\t30H\n"
		+ "\tJP\tC,F_F4_VAL_S_20\n"
		+ "\tCP\t3AH\n"
		+ "\tJP\tNC,F_F4_VAL_S_20\n"
      // Vorzeichen und Lesezeiger in Schattenregister
		+ "F_F4_VAL_S_4:\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tEXX\n"
      /*
       * B0=1:   Komma gelesen
       * C:      Dezimalexponent
       * H'L'HL: Mantisse
       * C':     Vorzeichen
       * D'E':   Lesezeiger
       */
		+ "\tLD\tBC,0000H\n"
		+ "\tLD\tH,B\n"
		+ "\tLD\tL,B\n"
		+ "\tJR\tF_F4_VAL_S_6\n"	// Zeichen bereits gelesen
		+ "F_F4_VAL_S_5:\n"
		+ "\tCALL\tF_F4_VAL_S_24\n"
		+ "F_F4_VAL_S_6:\n"
		+ "\tCP\t2EH\n"
		+ "\tJR\tNZ,F_F4_VAL_S_7\n"
      // Dezimalpunkt merken
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tJP\tNZ,F_F4_VAL_S_20\n"	// doppelter Dezimalpunkt
		+ "\tLD\tB,01H\n"
		+ "\tJR\tF_F4_VAL_S_5\n"
		+ "F_F4_VAL_S_7:\n"
      // Ziffer pruefen
		+ "\tSUB\t30H\n"
		+ "\tJR\tC,F_F4_VAL_S_9\n"
		+ "\tCP\t0AH\n"
		+ "\tJR\tNC,F_F4_VAL_S_9\n"
      // Mantisse bereits groesser 24 Bit?
		+ "\tLD\tD,A\n"
		+ "\tEXX\n"
		+ "\tLD\tA,H\n"
		+ "\tEXX\n"
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,F_F4_VAL_S_8\n"
		+ "\tLD\tA,D\n"
      /*
       * Mantisse kleiner/gleich 24 Bit
       *  -> Mantisse mit 10 multiplizieren und Ziffer addieren
       */
		+ "\tPUSH\tBC\n"
		+ "\tEXX\n"
		+ "\tPUSH\tBC\n"
		+ "\tEXX\n"
		+ "\tCALL\tI4_MUL_HL2HL_10_ADD_A\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
      // bei Nachkommastelle Dezimalexponent dekrementieren
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F_F4_VAL_S_5\n"	// -> naechstes Zeichen
		+ "\tDEC\tC\n"			// Dezimalexponent
		+ "\tJR\tF_F4_VAL_S_5\n"	// -> naechstes Zeichen
      /*
       * Mantisse groesser 24 Bit
       *  -> bei Vorkommastelle Dezimalexponent inkrementieren
       *  -> bei Nachkommastelle Ziffer ignorieren
       */
		+ "F_F4_VAL_S_8:\n"
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,F_F4_VAL_S_5\n"	// -> naechstes Zeichen
		+ "\tINC\tC\n"			// Dezimalexponent
		+ "\tJR\tF_F4_VAL_S_5\n"	// -> naechstes Zeichen
      // Exponent vorhanden?
		+ "F_F4_VAL_S_9:\n"
		+ "\tADD\tA,30H\n"		// Zeichen wiederherstellen
		+ "\tCP\t45H\n"			// 'E'
		+ "\tJR\tZ,F_F4_VAL_S_10\n"
		+ "\tCP\t65H\n"			// 'e'
		+ "\tJR\tNZ,F_F4_VAL_S_19\n"
      /*
       * Exponent parsen
       *   D7: Vorzeichen
       *   E:  Ergebnis
       */
		+ "F_F4_VAL_S_10:\n"
		+ "\tLD\tDE,0000H\n"
		+ "\tCALL\tF_F4_VAL_S_24\n"
		+ "\tCP\t2DH\n"			// Minuszeichen
		+ "\tJR\tNZ,F_F4_VAL_S_11\n"
		+ "\tDEC\tD\n"			// D7=1
		+ "\tJR\tF_F4_VAL_S_12\n"
		+ "F_F4_VAL_S_11:\n"
		+ "\tCP\t2BH\n"			// Pluszeichen
		+ "\tJR\tNZ,F_F4_VAL_S_13\n"
		+ "F_F4_VAL_S_12:\n"
		+ "\tCALL\tF_F4_VAL_S_24\n"
		+ "F_F4_VAL_S_13:\n"
      // Test auf Ziffer
		+ "\tCP\t30H\n"
		+ "\tJR\tC,F_F4_VAL_S_20\n"
		+ "\tCP\t3AH\n"
		+ "\tJR\tNC,F_F4_VAL_S_20\n"
      // Zeichen parsen
		+ "F_F4_VAL_S_14:\n"
		+ "\tSUB\t30H\n"
		+ "\tJR\tC,F_F4_VAL_S_15\n"
		+ "\tCP\t0AH\n"
		+ "\tJR\tNC,F_F4_VAL_S_15\n"
      // E = (E * 10) + A
		+ "\tPUSH\tBC\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tLD\tB,E\n"
		+ "\tLD\tA,E\n"
		+ "\tADD\tA,A\n"
		+ "\tADD\tA,A\n"
		+ "\tADD\tA,B\n"
		+ "\tADD\tA,A\n"
		+ "\tLD\tE,A\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tADD\tA,E\n"
		+ "\tCP\t27H\n"			// max. 38
		+ "\tPOP\tBC\n"
		+ "\tJR\tNC,F_F4_VAL_S_22\n"
		+ "\tLD\tE,A\n"
		+ "\tCALL\tF_F4_VAL_S_24\n"	// naehstes Zeichen
		+ "\tJR\tF_F4_VAL_S_14\n"
      /*
       * angegebenen Exponenten mit den vorhandenen
       * addieren bzw. subtrahieren:
       *   Exponent kleiner 0:  B = B - E
       *   Exponent groesser 0: B = B + E
       */
		+ "F_F4_VAL_S_15:\n"
		+ "\tADD\tA,30H\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tLD\tA,C\n"
		+ "\tBIT\t7,D\n"		// Vorzeichen Exponent
		+ "\tJR\tZ,F_F4_VAL_S_16\n"
		+ "\tSUB\tE\n"
		+ "\tJR\tF_F4_VAL_S_17\n"
		+ "F_F4_VAL_S_16:\n"
		+ "\tADD\tA,E\n"
		+ "F_F4_VAL_S_17:\n"
		+ "\tLD\tC,A\n"			// finaler Dezimalexponent
		+ "\tEX\tAF,AF\'\n"
		+ "\tJR\tF_F4_VAL_S_19\n"
      /*
       * Fliesskommazahl vollstaendig geparst
       *  -> Test auf nachfolgende Zeichen
       */
		+ "F_F4_VAL_S_18:\n"
		+ "\tCALL\tF_F4_VAL_S_24\n"
		+ "F_F4_VAL_S_19:\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F_F4_VAL_S_21\n"
		+ "\tCP\t20H\n"
		+ "\tJR\tZ,F_F4_VAL_S_18\n"
      // Syntaxfehler
		+ "F_F4_VAL_S_20:\n" );
      BasicUtil.appendSetErrorInvalidChars( compiler );
      buf.append( "\tJR\tF_F4_VAL_S_23\n"
      /*
       * FLoat-Zahl erzeugen
       *   C:      Dezimalexponent
       *   H'L'HL: Mantisse
       */
		+ "F_F4_VAL_S_21:\n"
		+ "\tEXX\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tDE\n"		// Mantisse nun in DEHL
		+ "\tCALL\tF4_I4_TO_F4\n"
      // Dezimalexponent aufloesen und Vorzeichen setzen
		+ "\tLD\tA,C\n"
		+ "\tCALL\tF4_RESOLVE_DEC_EXP\n"
		+ "\tJP\tNC,F4_UPDATE_SIGN\n"
      // numerischer Ueberlauf
		+ "F_F4_VAL_S_22:\n" );
      BasicUtil.appendSetErrorNumericOverflow( compiler );
      buf.append( "F_F4_VAL_S_23:\n"
		+ "\tCALL\tLD_DEHL_NULL\n"
		+ "\tSCF\n"
		+ "\tRET\n"
      // Zeichen lesen
		+ "F_F4_VAL_S_24:\n"
		+ "\tEXX\n"
		+ "\tLD\tA,(DE)\n"
		+ "\tINC\tDE\n"
		+ "\tEXX\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_I4_TO_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_RESOLVE_DEC_EXP );
      compiler.addLibItem( BasicLibrary.LibItem.I4_MUL_HL2HL_10_ADD_A );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_I2_SGN_F4 ) ) {
      /*
       * Test eines Float4-Wertes auf kleiner Null, Null
       * oder groesser Null
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   HL=-1: Float4-Wert < 0
       *   HL=0:  Float4-Wert == 0
       *   HL=1:  Float4-Wert > 0
       */
      buf.append( "F_I2_SGN_F4:\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"
		+ "\tINC\tHL\n"
		+ "\tBIT\t7,E\n"
		+ "\tRET\tZ\n"
		+ "\tDEC\tHL\n"
		+ "\tDEC\tHL\n"
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.S_STR_F4 ) ) {
      /*
       * Umwandlung eines numerischen Float4-Wertes
       * in eine Zeichenkette mit einer Dezimalzahl.
       * Das erste Zeichen enthaelt entweder das Vorzeichen
       * oder ein Leerzeichen.
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   HL:   Zeiger auf die Zeichenkette
       */
      buf.append( "S_STR_F4:\n"
		+ "\tCALL\tF4_TO_BCD\n"
      // Anzahl der Dezimalziffern in EHL ermitteln
		+ "\tLD\tB,06H\n"
		+ "\tLD\tA,E\n"
		+ "\tCALL\tS_STR_F4_12\n"
		+ "\tLD\tA,H\n"
		+ "\tCALL\tNC,S_STR_F4_12\n"
		+ "\tLD\tA,L\n"
		+ "\tCALL\tNC,S_STR_F4_12\n"
      // Sonderbehandlung bei 0
		+ "\tLD\tA,B\n"
		+ "\tOR\tA\n"
		+ "\tJP\tZ,S_STR_0\n"
      // Schreibpuffer
		+ "S_STR_F4_1:\n"
		+ "\tEXX\n"
		+ "\tLD\tHL,M_CVTBUF\n"
		+ "\tEXX\n"
      // Dezimal oder Exponentialdarstellung?
		+ "\tLD\tA,B\n"
		+ "\tADD\tA,C\n"		// A: Anz. Vorkommastellen
		+ "\tJP\tM,S_STR_F4_2\n"
		+ "\tCP\t07H\n"
		+ "\tJR\tC,S_STR_F4_7\n"	// -> Dezimalausgabe
		+ "S_STR_F4_2:\n"
		+ "\tCPL\n"
		+ "\tINC\tA\n"
		+ "\tADD\tA,B\n"
		+ "\tCP\t08H\n"
		+ "\tJR\tC,S_STR_F4_7\n"	// -> Dezimalausgabe
      // Exponentialdarstellung
		+ "\tPUSH\tBC\n"
		+ "\tLD\tA,01H\n"		// eine Vorkommastelle
		+ "\tSUB\tB\n"
		+ "\tLD\tC,A\n"
		+ "\tCALL\tS_STR_F4_9\n"
		+ "\tPOP\tBC\n"
      // auszugebender Dezimalexponent: B + C - 1
		+ "\tLD\tA,B\n"
		+ "\tADD\tA,C\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tZ,S_STR_F4_8\n"
		+ "\tEXX\n"
		+ "\tLD\t(HL),45H\n"		// E
		+ "\tINC\tHL\n"
		+ "\tLD\tB,2BH\n"		// Pluszeichen
		+ "\tJP\tP,S_STR_F4_3\n"	// Exponent groesser 0
		+ "\tCPL\n"
		+ "\tINC\tA\n"
		+ "\tLD\tB,2DH\n"		// Minuszeichen
		+ "S_STR_F4_3:\n"
		+ "\tLD\t(HL),B\n"
		+ "\tINC\tHL\n"
		+ "\tEXX\n"
      // Dezimalexponent in Dezimalzahl umwandeln
		+ "\tLD\tB,00H\n"		// Zehnerstelle
		+ "S_STR_F4_4:\n"
		+ "\tINC\tB\n"
		+ "\tSUB\t0AH\n"
		+ "\tJR\tNC,S_STR_F4_4\n"
		+ "\tADD\tA,0AH\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tLD\tA,B\n"
		+ "\tDEC\tA\n"
		+ "\tCALL\tS_STR_F4_5\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tCALL\tS_STR_F4_5\n"
		+ "\tJR\tS_STR_F4_8\n"
      // Wert in A als Ziffer in Ausgabepuffer schreiben
		+ "S_STR_F4_5:\n"
		+ "\tADD\tA,30H\n"
      // Zeichen in A in Ausgabepuffer schreiben
		+ "S_STR_F4_6:\n"
		+ "\tEXX\n"
		+ "\tLD\t(HL),A\n"
		+ "\tINC\tHL\n"
		+ "\tEXX\n"
		+ "\tRET\n"
      // Dezimaldarstellung
		+ "S_STR_F4_7:\n"		// Dezimalausgabe
		+ "\tCALL\tS_STR_F4_9\n"
		+ "S_STR_F4_8:\n"
		+ "\tEXX\n"
		+ "\tLD\t(HL),00H\n"
		+ "\tLD\tHL,M_CVTBUF\n"
		+ "\tRET\n"
      /*
       * Ausgabe einer Dezimalzahl mit Komma
       *
       * Parameter:
       *   B:   Anz. Dezimalstellen in EHL
       *   C:   Dezimalexponent
       *   EHL: BCD-Wert
       *   (M_SIGN): Vorzeichen in Bit 7
       *   HL': Zeiger auf den Ausgabepuffer
       */
		+ "S_STR_F4_9:\n"
		+ "\tLD\tA,(M_SIGN)\n"
		+ "\tAND\t80H\n"
		+ "\tLD\tA,20H\n"
		+ "\tJR\tZ,S_STR_F4_10\n"
		+ "\tLD\tA,2DH\n"		// Minuszeichen
		+ "S_STR_F4_10:\n"
		+ "\tCALL\tS_STR_F4_6\n"
		+ "\tLD\tD,00H\n"		// Steuerbyte
      // Anzahl Vorkommastellen im BCD-Wert: 6 + C
		+ "\tLD\tA,06H\n"
		+ "\tADD\tA,C\n"
		+ "\tLD\tC,A\n"			// Anzahl Vorkommastellen
      // Ziffern ausgeben
		+ "\tLD\tA,E\n"
		+ "\tCALL\tBCD_A_TO_DEC\n"
		+ "\tLD\tA,H\n"
		+ "\tCALL\tBCD_A_TO_DEC\n"
		+ "\tLD\tA,L\n"
		+ "\tCALL\tBCD_A_TO_DEC\n"
      // bei C (Dezimalexponent) > 0 weitere Nullen ausgeben
		+ "\tLD\tA,C\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"
		+ "\tRET\tM\n"
		+ "\tEXX\n"
		+ "S_STR_F4_11:\n"
		+ "\tLD\t(HL),30H\n"
		+ "\tINC\tHL\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,S_STR_F4_11\n"
		+ "\tEXX\n"
		+ "\tLD\tC,A\n"
		+ "\tRET\n"
      /*
       * Die Funktion dekrementiert B fuer jede in A
       * von links beginnend gefundene 0.
       * Wurde eine Nicht-Null gefunden,
       * kehrt die Funktion mit CY=1 zurueck.
       */
		+ "S_STR_F4_12:\n"
		+ "\tLD\tD,A\n"
		+ "\tAND\t0F0H\n"
		+ "\tLD\tA,D\n"
		+ "\tSCF\n"		// CY=1
		+ "\tRET\tNZ\n"
		+ "\tDEC\tB\n"
		+ "\tAND\t0FH\n"
		+ "\tLD\tA,D\n"
		+ "\tSCF\n"		// CY=1
		+ "\tRET\tNZ\n"
		+ "\tDEC\tB\n"
		+ "\tOR\tA\n"		// CY=0
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_BCD );
      compiler.addLibItem( BasicLibrary.LibItem.BCD_A_TO_DEC );
      compiler.addLibItem( BasicLibrary.LibItem.S_STR_0 );
      compiler.addLibItem( BasicLibrary.LibItem.M_CVTBUF );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_CSNG_D6 ) ) {
      /*
       * Umwandlung eines Dec6-Wertes in einen Float4-Wert
       *
       * Parameter:
       *   (M_ACCU): Dec64-Wert
       * Rueckgabewert:
       *   DEHL:     Float4-Wert
       */
      buf.append( "F_F4_CSNG_D6:\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tEXX\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tLD\tC,H\n"
		+ "\tLD\tDE,M_ACCU\n"
      /*
       * C:      Dezimalexponent
       * DE:     Lesezeiger
       * H'L'HL: Mantisse
       */
		+ "\tLD\tA,(DE)\n"
		+ "\tINC\tDE\n"
		+ "\tLD\t(M_SIGN),A\n"
      // erste Ziffer zur Mantisse hinzufuegen
		+ "\tCALL\tF_F4_CSNG_D6_3\n"
      // restliche Ziffern zur Mantisse hinzufuegen
		+ "\tLD\tB,05H\n"
		+ "F_F4_CSNG_D6_1:\n"
		+ "\tLD\tA,(DE)\n"
		+ "\tINC\tDE\n"
		+ "\tCALL\tF_F4_CSNG_D6_2\n"
		+ "\tDJNZ\tF_F4_CSNG_D6_1\n"
      // Float-Zahl erzeugen
		+ "\tEXX\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tDE\n"		// Mantisse nun in DEHL
		+ "\tCALL\tF4_I4_TO_F4\n"
      // Gesamtdezimalexponent ermitteln: C - Anz. Nachkommastellen
		+ "\tLD\tA,(M_SIGN)\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tAND\t07H\n"	// Anz. Nachkommastellen
		+ "\tSUB\tC\n"
		+ "\tCPL\n"
		+ "\tINC\tA\n"
      // Dezimalexponent aufloesen und Vorzeichen setzen
		+ "\tCALL\tF4_RESOLVE_DEC_EXP\n"
		+ "\tJP\tF4_UPDATE_SIGN\n"
      // 2 BCD-Ziffern zur Mantisse hinzufuegen
		+ "F_F4_CSNG_D6_2:\n"
		+ "\tPUSH\tAF\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tRRCA\n"
		+ "\tCALL\tF_F4_CSNG_D6_3\n"
		+ "\tPOP\tAF\n"
		+ "F_F4_CSNG_D6_3:\n"
      // Mantisse > 24 Bit?
		+ "\tEX\tAF,AF\'\n"
		+ "\tEXX\n"
		+ "\tLD\tA,H\n"
		+ "\tEXX\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F_F4_CSNG_D6_4\n"
      // Ziffer ignorieren und Dezimalexponent inkrementieren
		+ "\tINC\tC\n"
		+ "\tRET\n"
      // Ziffer zur Mantisse hinzufuegen
		+ "F_F4_CSNG_D6_4:\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tAND\t0FH\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tI4_MUL_HL2HL_10_ADD_A\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_I4_TO_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_RESOLVE_DEC_EXP );
      compiler.addLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.I4_MUL_HL2HL_10_ADD_A );
      compiler.addLibItem( BasicLibrary.LibItem.M_ACCU_6 );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_CSNG_I2 ) ) {
      /*
       * Umwandlung eines Int2-Wertes in einen Float4-Wert
       *
       * Parameter:
       *   HL:   Int2-Wert
       * Rueckgabewert:
       *   DEHL: Float4-Wert
       */
      buf.append( "F_F4_CSNG_I2:\n"
		+ "\tLD\tA,H\n"
		+ "\tOR\tA\n"
		+ "\tJP\tM,F_F4_CSNG_I2_1\n"
		+ "\tOR\tL\n"
		+ "\tJR\tNZ,F_F4_CSNG_I2_2\n"
		+ "\tLD\tD,A\n"			// Null
		+ "\tLD\tE,A\n"
		+ "\tRET\n"
      // negativer Wert
		+ "F_F4_CSNG_I2_1:\n"
		+ "\tCALL\tI2_NEG_HL\n"
		+ "\tCALL\tF_F4_CSNG_I2_2\n"
		+ "\tSET\t7,E\n"		// Vorzeichen
		+ "\tRET\n"
      // eigentliche Umwandlung
		+ "F_F4_CSNG_I2_2:\n"
		+ "\tLD\tDE,9600H\n"
		+ "\tJP\tF4_NORMALIZE\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.I2_NEG_HL );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_CSNG_I4 ) ) {
      /*
       * Umwandlung eines Long-Wertes in einen Float4-Wert
       *
       * Parameter:
       *   DEHL: Long-Wert
       * Rueckgabewert:
       *   DEHL: Float4-Wert
       */
      buf.append( "F_F4_CSNG_NEG_I4:\n"
		+ "\tCALL\tI4_NEG_DEHL\n"
		+ "\tCALL\tF4_I4_TO_F4\n"
		+ "\tSET\t7,E\n"		// Vorzeichen
		+ "\tRET\n" );
      buf.append( "F_F4_CSNG_I4:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJP\tM,F_F4_CSNG_NEG_I4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_I4_TO_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.I4_NEG_DEHL );
      // direkt weiter mit F4_I4_TO_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_I4_TO_F4 ) ) {
      /*
       * Umwandlung eines vorzeichenlosen Long-Wertes,
       * in einen Float4-Wert
       *
       * Parameter:
       *   DEHL: Long-Wert
       * Rueckgabewert:
       *   DEHL: Float4-Wert
       */
      buf.append( "F4_I4_TO_F4:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tOR\tE\n"
		+ "\tOR\tH\n"
		+ "\tOR\tL\n"
		+ "\tRET\tZ\n"			// Null
      // Exponent
		+ "\tLD\tB," );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
      // solange D != 0 ist, DEHL nach rechts schieben
		+ "F4_I4_TO_F4_1:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F4_I4_TO_F4_2\n"
		+ "\tSRL\tD\n"
		+ "\tRR\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tINC\tB\n"
		+ "\tJR\tF4_I4_TO_F4_1\n"
		+ "F4_I4_TO_F4_2:\n"
		+ "\tLD\tD,B\n"			// Exponent
		+ "\tJP\tF4_NORMALIZE\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_TO_BCD ) ) {
      /*
       * Umwandlung eines Float4-Wertes in einen BCD-Zahl.
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL:     Wert als BCD
       *   C:        Dezimalexponent
       *   (M_SIGN): Vorzeichen in Bit 7
       */
      buf.append( "F4_TO_BCD:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJP\tZ,F4_TO_BCD_16\n"
		+ "\tLD\tA,E\n"
		+ "\tLD\t(M_SIGN),A\n"
		+ "\tLD\tC,00H\n"		// Dezimalexponent
      /*
       * Solange Binaerexponent ohne Bias groesser oder gleich 40 ist,
       * durch 100000 teilen
       */
		+ "F4_TO_BCD_1:\n"
		+ "\tLD\tA,D\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 40 );
      buf.append( "\n"
		+ "\tJR\tC,F4_TO_BCD_2\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_DIVU_F4_100000\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tA,C\n"			// Dezimalexponent
		+ "\tADD\tA,05H\n"
		+ "\tLD\tC,A\n"
		+ "\tJR\tF4_TO_BCD_1\n"
      /*
       * Solange Binaerexponent ohne Bias groesser oder gleich 26 ist,
       * durch 10 teilen
       */
		+ "F4_TO_BCD_2:\n"
		+ "\tLD\tA,D\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 26 );
      buf.append( "\n"
		+ "\tJR\tC,F4_TO_BCD_3\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_DIVU_F4_10\n"
		+ "\tPOP\tBC\n"
		+ "\tINC\tC\n"			// Dezimalexponent
		+ "\tJR\tF4_TO_BCD_2\n"
      /*
       * Solange Binaerexponent ohne Bias kleiner oder gleich 6 ist,
       * mit 100000 multiplizieren
       */
		+ "F4_TO_BCD_3:\n"
		+ "\tLD\tA,D\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 7 );
      buf.append( "\n"
		+ "\tJR\tNC,F4_TO_BCD_4\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_MULU_F4_100000\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tA,C\n"			// Dezimalexponent
		+ "\tSUB\t05H\n"
		+ "\tLD\tC,A\n"
		+ "\tJR\tF4_TO_BCD_3\n"
      /*
       * Solange Binaerexponent ohne Bias groesser oder gleich 20 ist,
       * mit 10 multiplizieren
       */
		+ "F4_TO_BCD_4:\n"
		+ "\tLD\tA,D\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 21 );
      buf.append( "\n"
		+ "\tJR\tNC,F4_TO_BCD_5\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_MULU_F4_10\n"
		+ "\tPOP\tBC\n"
		+ "\tDEC\tC\n"			// Dezimalexponent
		+ "\tJR\tF4_TO_BCD_4\n"
      /*
       * Solange Binaerxponent ohne Bias groesser als 23 ist,
       * Mantisse nach links schieben
       */
		+ "F4_TO_BCD_5:\n"
		+ "\tSET\t7,E\n"
		+ "\tPUSH\tBC\n"		// Dezimalexponent retten
		+ "\tLD\tC,00\n"		// Ueberlaufbereich
		+ "F4_TO_BCD_6:\n"
		+ "\tLD\tA,D\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tJR\tZ,F4_TO_BCD_8\n"
		+ "\tJR\tC,F4_TO_BCD_7\n"	// sollte nie vorkommen
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tRL\tC\n"
		+ "\tDEC\tD\n"
		+ "\tJR\tF4_TO_BCD_6\n"
      /*
       * Solane Binaerxponent ohne Bias kleiner als 23 ist,
       * Mantisse nach rechts schieben
       */
		+ "F4_TO_BCD_7:\n"
		+ "\tCALL\tF4_TO_UINT\n"
      /*
       * Binaerxponent ohne Bias ist nun 23.
       * Mantisse in EHL in eine Dezimalzahl wandeln.
       * Komma wird durch den Dezimalexponenten festgelegt.
       */
		+ "F4_TO_BCD_8:\n"
		+ "\tLD\tA,C\n"		// Ueberlaufbereich, Werte 0..3
		+ "\tEXX\n"
		+ "\tCALL\tF4_TO_BCD_16\n"
		+ "\tLD\tL,A\n"		// Werte 0..3
		+ "\tLD\tB,18H\n"	// 24 Durchlaeufe
		+ "F4_TO_BCD_9:\n"
		+ "\tEXX\n"
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tEXX\n"
		+ "\tLD\tA,L\n"
		+ "\tADC\tA,A\n"
		+ "\tDAA\n"
		+ "\tLD\tL,A\n"
		+ "\tLD\tA,H\n"
		+ "\tADC\tA,A\n"
		+ "\tDAA\n"
		+ "\tLD\tH,A\n"
		+ "\tLD\tA,E\n"
		+ "\tADC\tA,A\n"
		+ "\tDAA\n"
		+ "\tLD\tE,A\n"
		+ "\tLD\tA,D\n"
		+ "\tADC\tA,A\n"
		+ "\tDAA\n"
		+ "\tLD\tD,A\n"
		+ "\tDJNZ\tF4_TO_BCD_9\n"
		+ "\tPOP\tBC\n"		// C: Dezimalexponent
      // auf 6 Dezimalstellen runden
		+ "F4_TO_BCD_10:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F4_TO_BCD_11\n"
		+ "\tLD\tA,L\n"
		+ "\tAND\t0FH\n"
      // letzte Stelle in A merken und von DEHL abschneiden
		+ "\tCALL\tF4_TO_BCD_14\n"
      // wenn abgeschnittene Ziffer 5..9 war, dann aufrunden
		+ "\tCP\t05H\n"
		+ "\tJR\tC,F4_TO_BCD_10\n"
		+ "\tLD\tA,L\n"
		+ "\tADD\tA,01H\n"
		+ "\tDAA\n"
		+ "\tLD\tL,A\n"
		+ "\tLD\tB,00H\n"
		+ "\tLD\tA,H\n"
		+ "\tADC\tA,B\n"
		+ "\tDAA\n"
		+ "\tLD\tH,A\n"
		+ "\tLD\tA,E\n"
		+ "\tADC\tA,B\n"
		+ "\tDAA\n"
		+ "\tLD\tE,A\n"
		+ "\tLD\tA,D\n"
		+ "\tADC\tA,B\n"
		+ "\tDAA\n"
		+ "\tLD\tD,A\n"
		+ "\tJR\tF4_TO_BCD_10\n"
      /*
       * Solange nach rechts schieben, bis rechts keine Null mehr steht,
       * Zur Sicherheit wird getestet, dass nicht nur Nullen vorhanden sind.
       */
		+ "F4_TO_BCD_11:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tE\n"
		+ "\tOR\tH\n"
		+ "\tOR\tL\n"
		+ "\tJR\tZ,F4_TO_BCD_13\n"
		+ "F4_TO_BCD_12:\n"
		+ "\tLD\tA,L\n"
		+ "\tAND\t0FH\n"
		+ "\tRET\tNZ\n"
		+ "\tCALL\tF4_TO_BCD_14\n"
		+ "\tJR\tF4_TO_BCD_12\n"
		+ "F4_TO_BCD_13:\n"
		+ "\tLD\tC,A\n"
		+ "\tRET\n"
      /*
       * eine Dezimalstelle nach rechts schieben,
       * Letzte Ziffer faellt heraus.
       */
		+ "F4_TO_BCD_14:\n"
		+ "\tLD\tB,04H\n"
		+ "F4_TO_BCD_15:\n"
		+ "\tSRL\tD\n"
		+ "\tRR\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tDJNZ\tF4_TO_BCD_15\n"
		+ "\tINC\tC\n"		// Dezimalexponent
		+ "\tRET\n"
      // C=D=E=H=L=0
		+ "F4_TO_BCD_16:\n"
		+ "\tCALL\tLD_DEHL_NULL\n"
		+ "\tLD\tC,D\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_10 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_TO_UINT );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_RESOLVE_DEC_EXP ) ) {
      /*
       * Dezimalexponent aufloesen
       *
       * Bei einem negativen Dezimalexponenten wird der Float4-Wert
       * solange duch 10 geteilt, bis der Dezimalexponent NUll ist.
       * Bei einem positiven Dezimalexponenten wird dagegen multipliziert.
       *
       * Parameter:
       *   A:    Dezimalexponent
       *   DEHL: Float4-Wert
       * Rueckgabe:
       *   DEHL: Float4-Wert
       *   CY=1: Ueberlauf
       */
      buf.append( "F4_RESOLVE_DEC_EXP:\n"
		+ "\tLD\tB,05H\n"		// 10000 = 10^5
		+ "F4_RESOLVE_DEC_EXP_1:\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"
		+ "\tJP\tM,F4_RESOLVE_DEC_EXP_4\n"
		+ "\tSUB\tB\n"
		+ "\tJR\tC,F4_RESOLVE_DEC_EXP_2\n"
		+ "\tLD\tC,A\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_MULU_F4_100000\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tLD\tA,C\n"
		+ "\tJR\tF4_RESOLVE_DEC_EXP_1\n"
		+ "F4_RESOLVE_DEC_EXP_2:\n"
		+ "\tADD\tA,B\n"
		+ "\tLD\tC,A\n"
		+ "F4_RESOLVE_DEC_EXP_3:\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_MULU_F4_10\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tDEC\tC\n"
		+ "\tJR\tNZ,F4_RESOLVE_DEC_EXP_3\n"
		+ "\tRET\n"
		+ "F4_RESOLVE_DEC_EXP_4:\n"
		+ "\tADD\tA,B\n"
		+ "\tJR\tZ,F4_RESOLVE_DEC_EXP_5\n"
		+ "\tJR\tC,F4_RESOLVE_DEC_EXP_6\n"
		+ "F4_RESOLVE_DEC_EXP_5:\n"
		+ "\tLD\tC,A\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_DIVU_F4_100000\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tLD\tA,C\n"
		+ "\tJR\tF4_RESOLVE_DEC_EXP_1\n"
		+ "F4_RESOLVE_DEC_EXP_6:\n"
		+ "\tSUB\tB\n"
		+ "\tLD\tC,A\n"
		+ "F4_RESOLVE_DEC_EXP_7:\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tF4_DIVU_F4_10\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tINC\tC\n"
		+ "\tJR\tNZ,F4_RESOLVE_DEC_EXP_7\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_10 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_TO_UINT ) ) {
      /*
       * Mantisse solange nach rechts schieben,
       * bis der Exponent ohne Bias 23 erreicht hat.
       * Damit enthaelt die Mantisse den ganzzahligen Anteil.
       * MSB wird gesetzt, falls es noch nicht der Fall sein sollte.
       * Das Vorzeichen wird ignoriert.
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabe:
       *   DEHL:   Float4-Wert mit Exponent ohne Bias >= 23
       *   B != 0: Es wurden Bits herausgeschoben.
       */
      buf.append( "F4_TO_UINT:\n"
		+ "\tLD\tB,00H\n"
		+ "\tSET\t7,E\n"
      // byteweise schieben?
		+ "\tLD\tA,D\n"
		+ "F4_TO_UINT_1:\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 16 );
      buf.append( "\n"
		+ "\tJR\tNC,F4_TO_UINT_3\n"
		+ "\tLD\tA,L\n"
		+ "\tOR\tB\n"
		+ "\tLD\tB,A\n"
		+ "\tLD\tL,H\n"
		+ "\tLD\tH,E\n"
		+ "\tLD\tE,00H\n"
		+ "\tLD\tA,D\n"
		+ "\tADD\tA,08H\n"
		+ "\tLD\tD,A\n"
		+ "\tJR\tF4_TO_UINT_1\n"
      // bitweise schieben
		+ "F4_TO_UINT_2:\n"
		+ "\tLD\tA,D\n"
		+ "F4_TO_UINT_3:\n"
		+ "\tCP\t" );
      buf.appendHex2( F4_BIAS + 23 );
      buf.append( "\n"
		+ "\tRET\tZ\n"
		+ "\tRET\tNC\n"
		+ "\tSRL\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tJR\tNC,F4_TO_UINT_4\n"
		+ "\tINC\tB\n"
		+ "F4_TO_UINT_4:\n"
		+ "\tINC\tD\n"
		+ "\tJR\tF4_TO_UINT_2\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_ADD_F4_1 ) ) {
      /*
       * Addition: DEHL = DEHL + 1
       */
      buf.append( "F4_ADD_F4_1:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_1F( buf );
      buf.append( "\tJR\tF4_ADD_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_SUB_F4_1 ) ) {
      /*
       * Subtraktion: DEHL = DEHL - 1
       */
      buf.append( "F4_SUB_F4_1:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, -1F );
      buf.append( "\tJR\tF4_ADD_F4_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_SUB_F4_F4 ) ) {
      /*
       * Subtraktion: DEHL = D'E'H'L' - DEHL
       */
      buf.append( "F4_SUB_F4_F4:\n"
		+ "\tCALL\tF4_NEG_F4\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NEG_F4 );
      // direkt weiter mit F4_ADD_F4_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_ADD_F4_F4 ) ) {
      /*
       * Addition: DEHL = D'E'H'L' + DEHL
       */
      buf.append( "F4_ADD_F4_F4:\n"
      // Zahl mit dem groesseren Exponenten nach DEHL
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tCP\tD\n"
		+ "\tJR\tC,F4_ADD_F4_F4_1\n"
		+ "\tEXX\n"
		+ "F4_ADD_F4_F4_1:\n"
      // Wenn exp' <= (exp - 24) dann DEHL zurueckgeben
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tSUB\tD\n"
		+ "\tEXX\n"
		+ "\tCP\t19H\n"
		+ "\tRET\tNC\n"
		+ "\tEX\tAF,AF\'\n"		// AF': Differenz Exp.
      // Vorzeichen vom ersten Operanden merken
		+ "\tLD\tA,E\n"
		+ "\tLD\t(M_SIGN),A\n"
      /*
       * Ermitteln, ob addiert oder subtrahiert werden muss,
       * Dabei MSBs setzen
       */
		+ "\tSET\t7,E\n"
		+ "\tEXX\n"
		+ "\tXOR\tE\n"
		+ "\tEX\tAF,AF\'\n"	// AF: Diff.Exp, AF': Add/Sub
		+ "\tSET\t7,E\n"
      // Exponenten angleichen
		+ "\tOR\tA\n"
		+ "\tJR\tZ,F4_ADD_F4_F4_3\n"
		+ "F4_ADD_F4_F4_2:\n"
		+ "\tSRL\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,F4_ADD_F4_F4_2\n"
		+ "F4_ADD_F4_F4_3:\n"
      // Verzweigen zu Addition bzw. Subtraktion
		+ "\tEX\tAF,AF\'\n"
		+ "\tJP\tP,F4_ADD_F4_F4_4\n"
      // Subtraktion
		+ "\tCALL\tF4_ADD_F4_F4_7\n"	// Vorzeichen aendern
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"		// Exponent der groessern Zahl
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tD,A\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tSBC\tHL,BC\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tA,E\n"
		+ "\tSBC\tA,C\n"
		+ "\tJR\tNC,F4_ADD_F4_F4_5\n"
      // Ueberlauf -> Mantisse AHL negieren und Vorzeichen aendern
		+ "\tLD\tB,H\n"
		+ "\tLD\tC,L\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tSBC\tHL,BC\n"
		+ "\tLD\tB,A\n"
		+ "\tLD\tA,00H\n"
		+ "\tSBC\tA,B\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tCALL\tF4_ADD_F4_F4_7\n"
		+ "\tEX\tAF,AF\'\n"
		+ "\tJR\tF4_ADD_F4_F4_5\n"
      // Addition
		+ "F4_ADD_F4_F4_4:\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
		+ "\tADD\tHL,BC\n"
		+ "\tEXX\n"
		+ "\tPUSH\tDE\n"
		+ "\tEXX\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tA,E\n"
		+ "\tADC\tA,C\n"
      // Ueberlauf -> Mantisse rechts schieben und Exponent inkrementieren
		+ "\tJR\tNC,F4_ADD_F4_F4_5\n"
		+ "\tRRA\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tRR\tB\n"
		+ "\tINC\tD\n"
		+ "\tJP\tZ,E_NUMERIC_OVERFLOW\n"
      // Berechnung in AHL fertig -> Test auf 0
		+ "F4_ADD_F4_F4_5:\n"
		+ "\tLD\tE,A\n"
		+ "\tOR\tH\n"
		+ "\tJR\tNZ,F4_ADD_F4_F4_6\n"
		+ "\tOR\tL\n"
		+ "\tJR\tNZ,F4_ADD_F4_F4_6\n"
      // Mantisse 0 (A=E=H=L=0) -> Ergebnis 0
		+ "\tLD\tD,A\n"
		+ "\tRET\n"
		+ "F4_ADD_F4_F4_6:\n"
		+ "\tCALL\tF4_NORMALIZE\n"
		+ "\tJP\tF4_UPDATE_SIGN\n"
      // Vorzeichen aendern
		+ "F4_ADD_F4_F4_7:\n"
		+ "\tLD\tA,(M_SIGN)\n"
		+ "\tXOR\t80H\n"
		+ "\tLD\t(M_SIGN),A\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_MUL_F4_2 ) ) {
      /*
       * Multiplikation DEHL = DEHL * 2.0
       */
      buf.append( "F4_MUL_F4_2:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"			// Null
		+ "\tINC\tD\n"
		+ "\tJP\tZ,E_NUMERIC_OVERFLOW\n"
		+ "\tINC\tD\n"
		+ "\tJP\tZ,E_NUMERIC_OVERFLOW\n"
		+ "\tDEC\tD\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_MUL_F4_10 ) ) {
      /*
       * Multiplikation DEHL = DEHL * 10.0
       */
      buf.append( "F4_MUL_F4_10:\n"
		+ "\tLD\tA,E\n"
		+ "\tLD\t(M_SIGN),A\n"
		+ "\tCALL\tF4_MULU_F4_10\n"
		+ "\tJP\tC,E_NUMERIC_OVERFLOW\n"
		+ "\tJP\tF4_UPDATE_SIGN\n"
      /*
       * vorzeichenlose Multiplikation DEHL = DEHL * 10.0
       */
		+ "F4_MULU_F4_10:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"			// Null
		+ "\tSET\t7,E\n"
		+ "\tLD\tA,E\n"
		+ "\tLD\tB,H\n"
		+ "\tLD\tC,L\n"
		+ "\tSRL\tA\n"
		+ "\tRR\tB\n"
		+ "\tRR\tC\n"
		+ "\tSRL\tA\n"
		+ "\tRR\tB\n"
		+ "\tRR\tC\n"
		+ "\tADD\tHL,BC\n"
		+ "\tADC\tA,E\n"
		+ "\tLD\tE,A\n"
		+ "\tLD\tA,03H\n"
		+ "\tJR\tNC,F4_MULU_F4_10_1\n"
		+ "\tRR\tE\n"
		+ "\tRR\tH\n"
		+ "\tRR\tL\n"
		+ "\tINC\tA\n"
		+ "F4_MULU_F4_10_1:\n"
		+ "\tADD\tA,D\n"
		+ "\tRET\tC\n"
		+ "\tJP\tZ,F4_MULU_F4_10_2\n"
		+ "\tCP\t0FFH\n"
		+ "\tJP\tZ,F4_MULU_F4_10_2\n"
		+ "\tLD\tD,A\n"
		+ "\tRES\t7,E\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n"
		+ "F4_MULU_F4_10_2:\n"
		+ "\tSCF\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_SQUARE_F4 ) ) {
      /*
       * Multiplikation DEHL = DEHL * DEHL
       */
      buf.append( "F4_SQUARE_F4:\n"
		+ "\tPUSH\tDE\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tPOP\tDE\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 );
      // direkt weiter mir F4_MUL_F4_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_MUL_F4_F4 ) ) {
      /*
       * Multiplikation DEHL = D'E'H'L' * DEHL
       */
      buf.append( "F4_MUL_F4_F4:\n"
		+ "\tCALL\tF4_DETERMINE_MUL_DIV_SIGN\n"
		+ "\tCALL\tF4_MULU_F4_F4\n"
		+ "\tJP\tC,E_NUMERIC_OVERFLOW\n"
		+ "\tJP\tF4_UPDATE_SIGN\n"
      /*
       * vorzeichenlose Multiplikation DEHL = DEHL * 100000.0
       */
		+ "F4_MULU_F4_100000:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_100000F( buf );
      /*
       * vorzeichenlose Multiplikation: DEHL = D'E'H'L' * DEHL
       *
       * Rueckgabe:
       *   DEHL: berechneter Wert
       *   CY=1: Ueberlauf
       */
      buf.append( "F4_MULU_F4_F4:\n"
      // Test auf 0
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\tZ\n"
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\tZ\n"
      // Exponenten addieren
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tCALL\tF4_ADD_EXP\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\tZ\n"			// Null
		+ "\tPUSH\tAF\n"
      // Mantisse von Operand 1 nach CDE laden
		+ "\tSET\t7,E\n"
		+ "\tEXX\n"
		+ "\tSET\t7,E\n"
		+ "\tLD\tC,E\n"
		+ "\tEX\tDE,HL\n"
      /*
       * Mantisse multiplizieren
       *
       * A:         temporaer verwendet
       * B:         Schleifenvariable
       * CDE:       Operand 1
       * E'H'L':    Operand 2
       * B'C'D'AHL: Ergebnis, davon die hoechstwertigen 24 Bit relevant
       */
		+ "\tXOR\tA\n"
		+ "\tLD\tH,A\n"
		+ "\tLD\tL,A\n"
		+ "\tEXX\n"
		+ "\tLD\tB,A\n"
		+ "\tLD\tC,A\n"
		+ "\tLD\tD,A\n"
		+ "\tEXX\n"
		+ "\tLD\tB,18H\n"
		+ "F4_MULU_F4_F4_3:\n"
      // Ergebnis links schieben
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tA\n"
		+ "\tEXX\n"
		+ "\tRL\tD\n"
		+ "\tRL\tC\n"
		+ "\tRL\tB\n"
      // Operand 2 links schieben
		+ "\tADD\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tEXX\n"
		+ "\tJR\tNC,F4_MULU_F4_F4_4\n"
		+ "\tADD\tHL,DE\n"
		+ "\tADC\tA,C\n"
		+ "\tPUSH\tAF\n"
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"
		+ "\tADC\tA,00H\n"
		+ "\tLD\tD,A\n"
		+ "\tLD\tA,C\n"
		+ "\tADC\tA,00H\n"
		+ "\tLD\tC,A\n"
		+ "\tLD\tA,B\n"
		+ "\tADC\tA,00H\n"
		+ "\tLD\tB,A\n"
		+ "\tEXX\n"
		+ "\tPOP\tAF\n"
		+ "F4_MULU_F4_F4_4:\n"
		+ "\tDJNZ\tF4_MULU_F4_F4_3\n"
		+ "\tEXX\n"
		+ "\tLD\tE,B\n"		// relevantes Ergebnis nach CDE
		+ "\tLD\tH,C\n"
		+ "\tLD\tL,D\n"		// EHL: relevantes Ergebnis
		+ "\tPOP\tBC\n"		// B: Exponent
		+ "\tLD\tD,B\n"
		+ "\tINC\tD\n"
		+ "\tSCF\n"
		+ "\tRET\tZ\n"
      // Berechnung fertig
		+ "\tLD\tB,A\n"		// herausgefallene Bits
		+ "\tJP\tF4_NORMALIZE_1\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_ADD_EXP );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DETERMINE_MUL_DIV_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_ADD_EXP ) ) {
      /*
       * Addition zweier Exponenten mit Ueberlaufpruefung
       *
       * Parameter:
       *   A, D: Exponenten
       * Rueckgabewert:
       *   D:    Ergebnis
       *   CY=1: Ueberlauf
       */
      buf.append( "F4_ADD_EXP:\n"
		+ "\tSUB\t" );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJR\tNC,F4_ADD_EXP_1\n"
		+ "\tADD\tA,D\n"
		+ "\tJR\tC,F4_ADD_EXP_2\n"
		+ "\tJP\tLD_DEHL_NULL\n"
		+ "F4_ADD_EXP_1:\n"
		+ "\tADD\tA,D\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "\tSCF\n"
		+ "\tRET\tZ\n"			// Ueberlauf
		+ "F4_ADD_EXP_2:\n"
		+ "\tLD\tD,A\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_DIV_F4_2 ) ) {
      /*
       * Division DEHL = DEHL / 2.0
       */
      buf.append( "F4_DIV_F4_2:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"		// Null
		+ "\tDEC\tD\n"
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_DIV_1_F4 ) ) {
      /*
       * Division DEHL = 1 / DEHL
       */
      buf.append( "F4_DIV_1_F4:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_1F( buf );
      buf.append( "\tEXX\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 );
      // direkt weiter mir F4_DIV_F4_F4
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_DIV_F4_F4 ) ) {
      /*
       * Division DEHL = D'E'H'L' / DEHL
       */
      buf.append( "F4_DIV_F4_F4:\n"
		+ "\tCALL\tF4_DETERMINE_MUL_DIV_SIGN\n"
		+ "\tCALL\tF4_DIVU_F4_F4\n"
		+ "\tJP\tC,E_NUMERIC_OVERFLOW\n"
		+ "\tJP\tF4_UPDATE_SIGN\n"
      /*
       * vorzeichenlose Division DEHL = DEHL / 100000.0
       */
		+ "F4_DIVU_F4_100000:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_100000F( buf );
      buf.append( "\tJR\tF4_DIVU_F4_F4\n"
      /*
       * vorzeichenlose Division DEHL = DEHL / 10.0
       */
		+ "F4_DIVU_F4_10:\n"
		+ "\tEXX\n" );
      append_LD_DEHL_F4( buf, 10F );
      /*
       * vorzeichenlose Division DEHL = D'E'H'L' / DEHL
       *
       * Rueckgabe:
       *   DEHL: berechneter Wert
       *   CY=1: Ueberlauf
       */
      buf.append( "F4_DIVU_F4_F4:\n"
      // Test auf 0
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJP\tZ,E_DIV_BY_0\n"
		+ "\tEXX\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\tZ\n"
      // Exponenten subtrahieren
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tSUB\tD\n"
		+ "\tJR\tNC,F4_DIVU_F4_F4_1\n"
		+ "\tADD\tA," );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tJP\tNC,LD_DEHL_NULL\n"
		+ "\tJR\tF4_DIVU_F4_F4_2\n"
		+ "F4_DIVU_F4_F4_1:\n"
		+ "\tADD\tA," );
      buf.appendHex2( F4_BIAS );
      buf.append( "\n"
		+ "\tRET\tC\n"			// Ueberlauf
		+ "F4_DIVU_F4_F4_2:\n"
		+ "\tLD\tD,A\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\tZ\n"			// Null
		+ "\tPUSH\tAF\n"		// neuer Exponent
      /*
       * Division der Mantisse E'H'L' / EHL,
       * Werte in die richtigen Register verteilen
       * und Ergebnisregister auf 0 setzen
       */
		+ "\tSET\t7,E\n"
		+ "\tLD\tD,L\n"
		+ "\tLD\tL,H\n"
		+ "\tLD\tH,E\n"
		+ "\tPUSH\tHL\n"
		+ "\tEXX\n"
		+ "\tSET\t7,E\n"
		+ "\tPOP\tBC\n"
		+ "\tLD\tA,L\n"
		+ "\tLD\tL,H\n"
		+ "\tLD\tH,E\n"
		+ "\tLD\tD,B\n"
		+ "\tLD\tE,C\n"
		+ "\tLD\tBC,0000H\n"
		+ "\tEXX\n"
		+ "\tLD\tH,A\n"
		+ "\tXOR\tA\n"
		+ "\tLD\tL,A\n"
		+ "\tLD\tE,A\n"
      /*
       * vorzeichenlose Division AB'C' = H'L'HL00 / D'E'D(E)
       *
       * Mit den letzten 16 angehaengten 0-Bits wird real nicht gerechnet.
       * Stattdessen wird der Divisor nach rechts geschoben.
       * Damit die Ungenauigkeit durch herausfallende Bits
       * nicht zu gross wird, gehen die letzten 8 herausgefallenen Bits
       * in die Berechnung mit ein.
       */
		+ "\tLD\tB,18H\n"
		+ "F4_DIVU_F4_F4_3:\n"
      // H'L'HL = H'L'HL - D'E'DE (Dividend = Dividend - Divisor)
		+ "\tOR\tA\n"			// CY=0
		+ "\tSBC\tHL,DE\n"
		+ "\tEXX\n"
		+ "\tSBC\tHL,DE\n"
		+ "\tEXX\n"
		+ "\tJR\tNC,F4_DIVU_F4_F4_4\n"	// kein Ueberlauf, CY=0
      // Uberlauf -> Subtraktion rueckgaengig machen, daduch CY=0
		+ "\tADD\tHL,DE\n"
		+ "\tEXX\n"
		+ "\tADC\tHL,DE\n"
		+ "\tEXX\n"
		+ "F4_DIVU_F4_F4_4:\n"
		+ "\tCCF\n"		// Subtraktion erfolgreich -> CY=1
      // Ergebnis links schieben und dabei Ergebnisbit CY hineinschieben
		+ "\tEXX\n"
		+ "\tRL\tC\n"
		+ "\tRL\tB\n"
		+ "\tEXX\n"
		+ "\tRLA\n"
      // CDE (Divisor) nach rechts schieben
		+ "\tEXX\n"
		+ "\tSRL\tD\n"
		+ "\tRR\tE\n"
		+ "\tEXX\n"
		+ "\tRR\tD\n"
		+ "\tRR\tE\n"
      // noch ein Durchlauf?
		+ "\tDJNZ\tF4_DIVU_F4_F4_3\n"
      // Ergebnis nach EHL bringen, herausgefallende Bits in B
		+ "\tEXX\n"
		+ "\tPUSH\tBC\n"
		+ "\tEXX\n"
		+ "\tPOP\tHL\n"
		+ "\tLD\tB,E\n"
		+ "\tLD\tE,A\n"
      // Exponent -> A
		+ "\tPOP\tAF\n"
		+ "\tLD\tD,A\n"
		+ "\tJR\tF4_NORMALIZE_1\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_DETERMINE_MUL_DIV_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.F4_NORMALIZE );
      compiler.addLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN );
      compiler.addLibItem( BasicLibrary.LibItem.LD_DEHL_NULL );
      compiler.addLibItem( BasicLibrary.LibItem.E_DIV_BY_0 );
      compiler.addLibItem( BasicLibrary.LibItem.E_NUMERIC_OVERFLOW );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_NORMALIZE ) ) {
      /*
       * Die Funktion verschiebt die Mantisse in EHL solange nach links,
       * bis MSB=1 ist.
       * Zusaetzlich wird auf Ueberlauf geprueft.
       *
       * Parameter:
       *   DEHL:   nicht normalisierter Float4-Wert
       *           mit korrekt gesetztem MSB
       * Rueckgabe:
       *   DEHL:   normalisierter Float4-Wert
       *   CY=1:   Ueberlauf (Exponent=0FFh)
       */
      buf.append( "F4_NORMALIZE:\n"
		+ "\tLD\tB,00H\n"
		+ "F4_NORMALIZE_1:\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"			// Null
		+ "F4_NORMALIZE_2:\n"
		+ "\tLD\tA,E\n"
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,F4_NORMALIZE_3\n"
      // Mantisse um 1 Byte nach links schieben
		+ "\tLD\tE,H\n"
		+ "\tLD\tH,L\n"
		+ "\tLD\tL,B\n"
		+ "\tLD\tA,D\n"
		+ "\tSUB\t08H\n"
		+ "\tLD\tD,A\n"
		+ "\tRET\tZ\n"			// Exponent=0 -> Abbruch
		+ "\tJR\tNC,F4_NORMALIZE_2\n"
		+ "\tLD\tD,00H\n"		// Exponent<0 -> Abbruch
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n"
		+ "F4_NORMALIZE_3:\n"
		+ "\tBIT\t7,E\n"
		+ "\tJR\tNZ,F4_NORMALIZE_4\n"
      // Mantisse um 1 Bit nach links schieben
		+ "\tSLA\tB\n"
		+ "\tADC\tHL,HL\n"
		+ "\tRL\tE\n"
		+ "\tDEC\tD\n"
		+ "\tRET\tZ\n"			// Exponent=0 -> Abbruch
		+ "\tJR\tF4_NORMALIZE_3\n"
		+ "F4_NORMALIZE_4:\n"
		+ "\tRES\t7,E\n"
		+ "\tINC\tD\n"			// Exponent=FFh?
		+ "\tSCF\n"
		+ "\tRET\tZ\n"
		+ "\tDEC\tD\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem(
		BasicLibrary.LibItem.F4_DETERMINE_MUL_DIV_SIGN ) )
    {
      /*
       * Berechnung des Vorzeichens des Ergebnisses fuer Multiplikation
       * und Division sowie Setzen des MSB in beiden Operanden
       *
       * Parameter:
       *   D'E'H'L': Operand 1
       *   DEHL:     Operand 2
       * Rueckgabe:
       *   A7:     Vorzeichen
       *   Z=1:      positives Vorzeichen
       */
      buf.append( "F4_DETERMINE_MUL_DIV_SIGN\n"
		+ "\tLD\tA,E\n"
		+ "\tEXX\n"
		+ "\tXOR\tE\n"
		+ "\tLD\t(M_SIGN),A\n"
		+ "\tEXX\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_MAX_F4_F4 ) ) {
      /*
       * Ermittlung des groesseren von zwei uebergebenen Float4-Werten
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "F4_MAX_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tRET\tC\n"
		+ "\tEXX\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_MIN_F4_F4 ) ) {
      /*
       * Ermittlung des kleineren von zwei uebergebenen Float4-Werten
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "F4_MIN_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tRET\tNC\n"
		+ "\tEXX\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_EQ_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' == DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_EQ_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\tNZ\n"
		+ "\tDEC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_GE_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' >= DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_GE_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0FFFFH\n"
		+ "\tRET\tNC\n"
		+ "\tINC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_GT_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' > DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_GT_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\tC\n"
		+ "\tRET\tZ\n"
		+ "\tDEC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_LE_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' <= DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_LE_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0FFFFH\n"
		+ "\tRET\tC\n"
		+ "\tRET\tZ\n"
		+ "\tINC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_LT_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' < DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_LT_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\tNC\n"
		+ "\tDEC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.I2_NE_F4_F4 ) ) {
      /*
       * Vergleich zweier Float4-Wert: D'E'H'L' == DEHL ?
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       */
      buf.append( "I2_NE_F4_F4:\n"
		+ "\tCALL\tF4_CMP_F4_F4\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\tZ\n"
		+ "\tDEC\tHL\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_CMP_F4_F4 ) ) {
      /*
       * Vergleich zweier FLoat4-Werte
       * Die Register mit den FLoat4-Werte werden nicht geaendert.
       *
       * Parameter:
       *   D'E'H'L': Float4-Wert 1
       *   DEHL:     Float4-Wert 2
       * Rueckgabewert:
       *   Z=0:  beide Zahlen sind gleiche
       *   CY=1: Wert D'E'H'L' ist kleiner als der Wert in DEHL
       */
      buf.append( "F4_CMP_F4_F4:\n"
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tOR\tD\n"
		+ "\tRET\tZ\n"		// beide Null
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"		// CY=0
		+ "\tEXX\n"
		+ "\tJR\tNZ,F4_CMP_F4_F4_1\n"
      // Op1 = 0, Op2 != 0 -> CY = negiertes Vorzeichen von Op2
		+ "\tLD\tA,E\n"
		+ "\tJR\tF4_CMP_F4_F4_5\n"
		+ "F4_CMP_F4_F4_1:\n"
      // Op2 != 0
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"		// CY=0
		+ "\tJR\tNZ,F4_CMP_F4_F4_3\n"
      // Op2 != 0, Op1 = 0 -> CY = Vorzeichen von Op1
		+ "\tEXX\n"
		+ "F4_CMP_F4_F4_2:\n"
		+ "\tLD\tA,E\n"		// Vorzeichen von Op1 -> CY
		+ "\tEXX\n"
		+ "\tJR\tF4_CMP_F4_F4_6\n"
		+ "F4_CMP_F4_F4_3:\n"
      // Op1 != 0 und Op2 != 0 -> Vorzeichen vergleichen
		+ "\tLD\tA,E\n"
		+ "\tEXX\n"
		+ "\tXOR\tE\n"
		+ "\tJP\tM,F4_CMP_F4_F4_2\n"	// unterschiedl. Vorz.
      // Vorzeichen gleich -> Vorzeichen nach B und Werte vergleichen
		+ "\tLD\tA,D\n"
		+ "\tEXX\n"
		+ "\tLD\tB,E\n"			// Vorzeichen
		+ "\tCP\tD\n"
		+ "\tJR\tNZ,F4_CMP_F4_F4_4\n"
		+ "\tEXX\n"
		+ "\tLD\tA,E\n"
		+ "\tEXX\n"
		+ "\tCP\tE\n"
		+ "\tJR\tNZ,F4_CMP_F4_F4_4\n"
		+ "\tEXX\n"
		+ "\tLD\tA,H\n"
		+ "\tEXX\n"
		+ "\tCP\tH\n"
		+ "\tJR\tNZ,F4_CMP_F4_F4_4\n"
		+ "\tEXX\n"
		+ "\tLD\tA,L\n"
		+ "\tEXX\n"
		+ "\tCP\tL\n"
		+ "\tRET\tZ\n"
		+ "F4_CMP_F4_F4_4:\n"
		+ "\tLD\tA,B\n"		// CY und B7 kombinieren
		+ "\tJR\tNC,F4_CMP_F4_F4_6\n"
		+ "F4_CMP_F4_F4_5:\n"
		+ "\tXOR\t80H\n"
		+ "F4_CMP_F4_F4_6:\n"
		+ "\tOR\t7FH\n"		// Z=0
		+ "\tRLA\n"
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_CMPU_MEM_F4 ) ) {
      /*
       * Die Routine vergleicht vorzeichenlos den FLoat4-Wert,
       * auf den BC zeigt, mit dem Float4-Wert in DEHL.
       *
       * Parameter:
       *   (BC): Zeiger auf das letzte Byte (Exponent) eines Float4-Wertes
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   Z=0:  beide Zahlen sind gleich
       *   CY=1: Wert in (BC) ist kleiner als der Wert in DEHL
       */
      buf.append( "F4_CMPU_MEM_F4:\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tCP\tD\n"
		+ "\tRET\tNZ\n"
      // gleiche Exponenten
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"		// beide Zahlen Null
		+ "\tDEC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tCP\tE\n"
		+ "\tRET\tNZ\n"
		+ "\tDEC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tCP\tH\n"
		+ "\tRET\tNZ\n"
		+ "\tDEC\tBC\n"
		+ "\tLD\tA,(BC)\n"
		+ "\tCP\tH\n"
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_NEG_F4 ) ) {
      /*
       * Die Routine negiert den FLoat4-Wert in DEHL.
       *
       * Parameter:
       *   DEHL: Float4-Wert
       * Rueckgabewert:
       *   DEHL: Float4-Wert
       */
      buf.append( "F4_NEG_F4:\n"
		+ "\tLD\tA,E\n"
		+ "\tXOR\t80H\n"
		+ "\tLD\tE,A\n"
		+ "\tRET\n" );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_UPDATE_SIGN ) ) {
      /*
       * Die Funktion ersetzt C7 durch das Vorzeichen in M_SIGN.
       */
      buf.append( "F4_UPDATE_SIGN:\n"
		+ "\tRES\t7,E\n"
		+ "\tLD\tA,(M_SIGN)\n"
		+ "\tAND\t80H\n"
		+ "\tOR\tE\n"
		+ "\tLD\tE,A\n"
		+ "\tRET\n" );
      compiler.addLibItem( BasicLibrary.LibItem.M_SIGN );
    }
  }


  public static void appendDataTo( BasicCompiler compiler, AsmCodeBuf buf )
  {
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_0_5 ) ) {
      buf.append( "C_F4_0_5:\n" );
      append_DB_F4( buf, 0.5F );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_0_75 ) ) {
      buf.append( "C_F4_0_75:\n" );
      append_DB_F4( buf, 0.75F );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_0_9997 ) ) {
      buf.append( "C_F4_0_9997:\n" );
      append_DB_F4( buf, 0.9997F );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_1 ) ) {
      buf.append( "C_F4_1:\n" );
      append_DB_F4( buf, 1F );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_1_0007 ) ) {
      buf.append( "C_F4_1_0007:\n" );
      append_DB_F4( buf, 1.0007F );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_HALFPI ) ) {
      buf.append( "C_F4_HALFPI:\n" );
      append_DB_F4( buf, (float) (Math.PI * 0.5) );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_PI ) ) {
      buf.append( "C_F4_PI:\n" );
      append_DB_F4( buf, (float) Math.PI );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_15PI ) ) {
      buf.append( "C_F4_15PI:\n" );
      append_DB_F4( buf, (float) (Math.PI * 1.5) );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_DOUBLEPI ) ) {
      buf.append( "C_F4_DOUBLEPI:\n" );
      append_DB_F4( buf, (float) (Math.PI * 2.0) );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.C_F4_EULER ) ) {
      buf.append( "C_F4_EULER:\n" );
      append_DB_F4( buf, (float) (Math.PI * 2.0) );
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_ATN_F4 ) ) {
      buf.append( "TAB_ATN_COEFF:\n"
		+ "\tDB\t" );
      buf.appendHex2( ATN_COEFF_CNT );
      buf.append( '\n' );
      int     n   = 3;
      boolean neg = true;
      for( int i = 0; i < ATN_COEFF_CNT; i++ ) {
	float f = 1F / (float) n;
	if( neg ) {
	  f = -f;
	}
	append_DB_F4( buf, f );
	neg = !neg;
	n += 2;
      }
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_EXP_F4 ) ) {
      buf.append( "TAB_EXP_COEFF:\n"
		+ "\tDB\t" );
      buf.appendHex2( EXP_COEFF_CNT );
      buf.append( '\n' );
      int n = 2;
      int q = 1;
      for( int i = 0; i < EXP_COEFF_CNT; i++ ) {
	q *= n;
	append_DB_F4( buf, 1F / (float) q );
	n++;
      }
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_LN_F4 ) ) {
      buf.append( "TAB_LN_COEFF:\n"
		+ "\tDB\t" );
      buf.appendHex2( LN_COEFF_CNT );
      buf.append( '\n' );
      int     n   = 3;
      for( int i = 0; i < LN_COEFF_CNT; i++ ) {
	append_DB_F4( buf, 1F / (float) n );
	n += 2;
      }
    }
    if( compiler.usesLibItem( BasicLibrary.LibItem.F_F4_SIN_F4 ) ) {
      buf.append( "TAB_SIN_COEFF:\n"
		+ "\tDB\t" );
      buf.appendHex2( SIN_COEFF_CNT );
      buf.append( '\n' );
      boolean neg = true;
      for( int i = 1; i <= SIN_COEFF_CNT; i++ ) {
	int n = i + i + 1;
	int q = 1;
	for( int k = 1; k <= n; k++ ) {
	  q *= k;
	}
	float f = 1F / (float) q;
	if( neg ) {
	  f = -f;
	}
	append_DB_F4( buf, f );
	neg = !neg;
	n += 2;
      }
    }
  }


  public static void appendBssTo( BasicCompiler compiler, AsmCodeBuf buf )
  {
    if( compiler.usesLibItem( BasicLibrary.LibItem.F4_CALC_SERIES ) ) {
      buf.append( "M_TABPTR:\n"
		+ "\tDS\t2\n" );
    }
  }


	/* --- Konstruktor --- */

  private FloatLibrary()
  {
    // nicht intstanziierbar
  }


	/* --- private Methoden --- */

  private static void append_LD_DEHL_1F( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, 1F );
  }


  private static void append_LD_DEHL_100000F( AsmCodeBuf buf )
  {
    append_LD_DEHL_F4( buf, 100000F );
  }


  private static void append_LD_DEHL_F4( AsmCodeBuf buf, float f )
  {
    int m = toIntBits( f );
    buf.append_LD_DE_nn( m >> 16 );
    buf.append_LD_HL_nn( m );
  }


  private static void append_DB_F4( AsmCodeBuf buf, float f )
  {
    int m = toIntBits( f );
    buf.append( "\tDB\t" );
    for( int i = 0; i < 4; i++ ) {
      if( i > 0 ) {
	buf.append( ',' );
      }
      buf.appendHex2( m );
      m >>= 8;
    }
    buf.append( '\n' );
  }


  private static int toIntBits( float f )
  {
    int m = Float.floatToIntBits( f );
    int e = ((m >> 23) & 0xFF) - 0x7F + F4_BIAS;
    return ((e << 24) & 0xFF000000)		// Exponent
		| ((m >> 8) & 0x00800000)	// Vorzeichen
		| (m & 0x007FFFFF);		// Mantisse
  }
}

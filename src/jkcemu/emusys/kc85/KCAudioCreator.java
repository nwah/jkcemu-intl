/*
 * (c) 2011-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Lesen einer KCB-, KCC- oder KC-TAP-Datei als Audiodaten
 *
 * Die Klasse wandelt ein Speicherabbild in Audiodaten
 * entsprechend dem KC85- und Z9001-Kassettenaufzeichnungsformat um.
 * Laut KC85/3- bzw. KC85/4-Systemhandbuch arbeitet die Kassettenaufzeichnung
 * mit den Frequenzen 600, 1200 und 2400 Hz.
 * In der Realitaet werden aber etwa 10% bis 15% niedrigere Frequenzen
 * verwendet, wobei sich das zwischen den jeweiligen Computer-Typen
 * etwas unterscheidet.
 * Es wird hier mit einer Abtastrate von 22050 Hz gearbeitet und
 * daraus durch ganzahlige Teilung die Frequenzen 551, 1102 und 2205 Hz
 * erzeugt.
 */

package jkcemu.emusys.kc85;

import java.io.IOException;
import jkcemu.audio.AudioOut;
import jkcemu.audio.BitSampleBuffer;
import jkcemu.base.ByteIterator;
import jkcemu.file.FileInfo;


public class KCAudioCreator extends BitSampleBuffer
{
  private static int FRAME_RATE = 22050;

  private boolean phase;


  public KCAudioCreator(
		boolean tapFmt,
		int     blkNum,		// nur bei tapFmt == false relevant
		byte[]  dataBytes,
		int     offs,
		int     len,
		boolean appendPause ) throws IOException
  {
    super( FRAME_RATE, 0x8000 );
    this.phase = false;

    ByteIterator iter = new ByteIterator( dataBytes, offs, len );
    if( tapFmt && !skipString( iter, FileInfo.KCTAP_MAGIC ) ) {
      throw new IOException( "KC-TAP-Kopf erwartet" );
    }

    boolean firstBlk = true;
    while( iter.hasNext() ) {

      // Vorton
      int nHalf = 320;		// Anzahl Halbschwingungen
      if( tapFmt ) {
	/*
	 * Beginn einer neuen Teildatei innerhalb einer Multi-TAP-Datei?
	 * Wenn ja, dann Header uerberspringen und langer Vorton
	 */
	if( skipString( iter, FileInfo.KCTAP_MAGIC ) ) {
	  nHalf = 8000;
	}
      }
      if( firstBlk ) {
	nHalf = 8000;		// beim 1. Block immer langer Vorton
      } else {
	addSamples( 240, this.phase );		// kurze Pause
      }
      for( int i = 0; i < nHalf; i++ ) {
	addPhaseChangeSamples( 10 );
      }

      // Trennschwingung
      addPhaseChangeSamples( 20 );
      addPhaseChangeSamples( 20 );

      // Blocknummer
      int b = 0;
      if( tapFmt ) {
	b = iter.readByte();
      } else {
	b = ((firstBlk || (iter.available() > 128)) ? blkNum++ : 0xFF);
      }
      addByteSamples( b );

      // Datenbytes
      int cks = 0;
      for( int i = 0; i < 128; i++ ) {
	b = iter.readByte();
	addByteSamples( b );
	cks = (cks + b) & 0xFF;
      }

      // Pruefsumme
      addByteSamples( cks );
      firstBlk = false;
    }

    // abschliessender Phasenwechsel
    if( getFrameCount() > 0 ) {
      addPhaseChangeSamples( 20 );
    }

    // nachfolgende Pause
    if( appendPause ) {
      addSamples( FRAME_RATE * AudioOut.TRAILING_PAUSE_MS / 1000, false );
    }
  }


  public KCAudioCreator(
		boolean tapFmt,
		int     blkNum,		// nur bei tapFmt == false relevant
		byte[]  dataBytes,
		int     offs,
		int     len ) throws IOException
  {
    this( tapFmt, blkNum, dataBytes, offs, dataBytes.length, true );
  }


  public KCAudioCreator(
		boolean tapFmt,
		int     blkNum,		// nur bei tapFmt == false relevant
		byte[]  dataBytes ) throws IOException
  {
    this( tapFmt, blkNum, dataBytes, 0, dataBytes.length, true );
  }


	/* --- private Methoden --- */

  private void addPhaseChangeSamples( int value ) throws IOException
  {
    this.phase = !this.phase;
    addSamples( value, this.phase );
  }


  private void addByteSamples( int value ) throws IOException
  {
    for( int i = 0; i < 8; i++ ) {
      if( (value & 0x01) != 0 ) {
	addPhaseChangeSamples( 10 );
	addPhaseChangeSamples( 10 );
      } else {
	addPhaseChangeSamples( 5 );
	addPhaseChangeSamples( 5 );
      }
      value >>= 1;
    }
    addPhaseChangeSamples( 20 );
    addPhaseChangeSamples( 20 );
  }


  private static boolean skipString( ByteIterator iter, String text )
  {
    boolean rv     = true;
    int     begPos = iter.getIndex();
    int     len    = text.length();
    for( int i = 0; i < len; i++ ) {
      if( iter.readByte() != text.charAt( i ) ) {
	iter.setIndex( begPos );
	rv = false;
	break;
      }
    }
    return rv;
  }
}

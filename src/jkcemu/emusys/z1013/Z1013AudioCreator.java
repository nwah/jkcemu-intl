/*
 * (c) 2011-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Lesen einer Z1013-Datei als Audiodaten
 *
 * Die Klasse wandelt ein Speicherabbild in Audiodaten
 * entsprechend dem Z1013-Kassettenaufzeichnungsformat um.
 * Im Original werden die Frequenzen 640, 1280 und 2560 Hz verwendet
 * (leicht gerundet). Diese werden hier mit einer Abtastrate von 22050 Hz
 * abgebildet, wobei sich Frequenzen von 689, 1378 und 2756 Hz ergeben.
 * Diese 4,7% Abweichung werden problemlos von den Z1013-Laderoutinen
 * eingelesen.
 */

package jkcemu.emusys.z1013;

import java.io.IOException;
import java.util.NoSuchElementException;
import jkcemu.audio.AudioOut;
import jkcemu.audio.BitSampleBuffer;
import jkcemu.base.ByteIterator;


public class Z1013AudioCreator extends BitSampleBuffer
{
  private static int FRAME_RATE = 22050;

  private boolean phase;


  public Z1013AudioCreator(
			boolean headersave,
			byte[]  dataBytes,
			int     offs,
			int     len ) throws IOException
  {
    super( FRAME_RATE, 0x8000 );
    this.phase = false;

    ByteIterator iter = new ByteIterator( dataBytes, offs, len );

    // Headersave-Kennung testen und Anfangsadresse lesen
    int begAddr = 0;
    if( headersave ) {
      iter.setIndex( offs + 13 );
      for( int i = 0; i < 3; i++ ) {
	if( iter.readByte() != 0xD3 ) {
	  headersave = false;
	  break;
	}
      }
      if( headersave ) {
	iter.setIndex( offs );
	begAddr = iter.readWord();
	if( begAddr < 0 ) {
	  begAddr = 0;
	}
      }
    }

    // Bloecke erzeugen
    int blkIdx  = 0;
    int blkAddr = 0;
    iter.setIndex( offs );
    while( iter.hasNext() ) {

      /*
       * Vorton, 1. Halbschwingung,
       * ab dem 2. Block vergroessert (ergibt eine kurze Pause)
       */
      addPhaseChangeSamples( blkIdx > 0 ? 55 : 17 );

      // Vorton, restliche Halbschwingungen
      int nHalf = 27;				// (14 * 2) - 1
      if( (blkIdx == 0) || ((blkIdx == 1) && headersave) ) {
	nHalf= 3999;				// (2000 * 2) - 1
      }
      for( int i = 0; i < nHalf; i++ ) {	// weitere Halbschwingungen
	addPhaseChangeSamples( 16 );
      }

      // Trennschwingung
      addPhaseChangeSamples( 8 );
      addPhaseChangeSamples( 8 );

      // Blockadresse
      int w = 0;
      if( headersave ) {
	if( blkIdx == 0 ) {
	  blkAddr = 0x00E0;
	} else if( blkIdx == 1 ) {
	  blkAddr = begAddr;
	} else {
	  blkAddr += 0x0020;
	}
	w = blkAddr;
      }
      addWordSamples( w );
      int cks = w;
      blkIdx++;

      // Datenwoerter
      for( int i = 0; i < 16; i++ ) {
	int b0 = iter.readByte();
	w      = ((iter.readByte() << 8) & 0xFF00) | (b0 & 0x00FF);
	addWordSamples( w );
	cks += w;
      }

      // Pruefsumme
      addWordSamples( cks );
    }

    // abschliessende Schwingung
    if( getFrameCount() > 0 ) {
      addPhaseChangeSamples( 8 );
      addPhaseChangeSamples( 8 );
    }

    // nachfolgende Pause
    addSamples( FRAME_RATE * AudioOut.TRAILING_PAUSE_MS / 1000, false );
  }


  public Z1013AudioCreator(
			boolean headersave,
			byte[]  dataBytes ) throws IOException
  {
    this( headersave, dataBytes, 0, dataBytes.length );
  }


	/* --- private Methoden --- */

  private void addPhaseChangeSamples( int value ) throws IOException
  {
    this.phase = !this.phase;
    addSamples( value, this.phase );
  }


  private void addWordSamples( int value ) throws IOException
  {
    for( int i = 0; i < 16; i++ ) {
      if( (value & 0x01) != 0 ) {
	addPhaseChangeSamples( 8 );
      } else {
	addPhaseChangeSamples( 4 );
	addPhaseChangeSamples( 4 );
      }
      value >>= 1;
    }
  }
}

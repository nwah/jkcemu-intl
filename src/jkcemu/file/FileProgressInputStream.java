/*
 * (c) 2008-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dateieingabestrom, der einen Fortschrittsbalken bedient
 *
 * Bei Uebergabe eines Window-Objekts wird zusaetzlich der Fortschritt
 * am Fenster- bzw. Applikations-Icon visualisiert,
 * sofern die Java-Laufzeitumgebung dies unterstuetzt.
 */

package jkcemu.file;

import java.awt.EventQueue;
import java.awt.Window;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Checksum;
import javax.swing.JProgressBar;
import jkcemu.base.DesktopHelper;
import jkcemu.base.EmuUtil;


public class FileProgressInputStream extends InputStream
{
  private static final int DEFAULT_BUFFER_SIZE = 0x4000;


  private BufferedInputStream in;
  private long                fileSize;
  private long                readPos;
  private long                readEnd;
  private float               lastProgressValue;
  private int                 progressMinValue;
  private int                 progressMaxValue;
  private long                progressUpdPeriod;
  private long                progressUpdCounter;
  private float               progressRange;
  private JProgressBar        progressBar;
  private Window              windowForAnimation;


  public FileProgressInputStream(
			File         file,
			JProgressBar progressBar,
			Window       windowForAnimation,
			Checksum     cks ) throws IOException
  {
    this.windowForAnimation = windowForAnimation;
    this.progressBar        = progressBar;
    this.lastProgressValue  = -1F;
    if( progressBar != null ) {
      this.progressMinValue = progressBar.getMinimum();
      this.progressMaxValue = progressBar.getMaximum();
    } else {
      this.progressMinValue = 0;
      this.progressMaxValue = 0;
    }
    fireProgressValue( 0F );

    this.fileSize = file.length();
    if( this.fileSize < 0 ) {
      this.fileSize = 0;
    }
    this.readPos = 0;
    this.readEnd = this.fileSize;

    int bufSize = DEFAULT_BUFFER_SIZE;
    if( this.fileSize < bufSize ) {
      bufSize = (int) (this.fileSize > 0 ? this.fileSize : 1);
    }
    this.in = new BufferedInputStream(
				new FileInputStream( file ),
				bufSize );
    if( cks != null ) {
      this.readEnd *= 2;
      initProgressBar();
      try {
	int b = read();
	while( b >= 0 ) {
	  cks.update( b );
	  b = read();
	}
      }
      finally {
	EmuUtil.closeSilently( this.in );
      }
      this.in = new BufferedInputStream(
				new FileInputStream( file ),
				bufSize );
    } else {
      initProgressBar();
    }
  }


  public long getFileSize()
  {
    return this.fileSize;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void close() throws IOException
  {
    if( this.readPos == this.readEnd ) {
      fireProgressValue( 1F );
    }
    this.in.close();
  }


  @Override
  public int read() throws IOException
  {
    int b = this.in.read();
    if( b >= 0 ) {
      progressUpd( 1 );
    }
    return b;
  }


  @Override
  public int read( byte[] buf, int offs, int len ) throws IOException
  {
    int rv = this.in.read( buf, offs, len );
    if( rv > 0 ) {
      progressUpd( rv );
    }
    return rv;
  }


  @Override
  public long skip( long n ) throws IOException
  {
    long rv = this.in.skip( n );
    if( rv > 0 ) {
      progressUpd( rv );
    }
    return rv;
  }


	/* --- private Methoden --- */

  private void fireProgressValue( final float value )
  {
    if( (this.progressBar != null) && (this.windowForAnimation != null) ) {
      int barValue = this.progressMinValue
			+ Math.round( value * this.progressRange );
      if( barValue > this.progressMaxValue ) {
	barValue = this.progressMaxValue;
      }
      final JProgressBar progressBar        = this.progressBar;
      final Window       windowForAnimation = this.windowForAnimation;
      final int          barValue1          = barValue;
      EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    if( progressBar != null ) {
		      progressBar.setValue( barValue1 );
		    }
		    if( windowForAnimation != null ) {
		      DesktopHelper.setProgressValue(
						windowForAnimation,
						value );
		    }
		  }
		} );
    }
  }


  private void initProgressBar()
  {
    int range = this.progressMaxValue - this.progressMinValue;
    if( range > 0 ) {
      this.progressUpdPeriod = this.readEnd / (long) range;
    } else {
      this.progressUpdPeriod = 0;
    }
    this.progressRange      = (float) range;
    this.progressUpdCounter = 0;
  }


  private void progressUpd( long diffBytes )
  {
    this.readPos            += diffBytes;
    this.progressUpdCounter += diffBytes;
    if( (this.progressUpdCounter >= this.progressUpdPeriod)
	&& (this.readEnd > 0) )
    {
      float value = (float) this.readPos / (float) this.readEnd;
      if( value != this.lastProgressValue ) {
	this.lastProgressValue = value;
	fireProgressValue( value );
      }
      this.progressUpdCounter = 0;
    }
  }
}

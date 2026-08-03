/*
 * (c) 2008-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * GZip-Entpacker
 */

package jkcemu.file;

import java.awt.Frame;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import jkcemu.base.EmuUtil;
import jkcemu.base.AbstractThreadFrm;


public class GZipUnpacker extends AbstractThreadFrm
{
  private Window owner;
  private File   srcFile;
  private File   outFile;


  public static void unpackFile( Window owner, File srcFile, File outFile )
  {
    Frame frm = new GZipUnpacker( owner, srcFile, outFile );
    frm.setTitle( "GZIP-Datei entpacken" );
    frm.setVisible( true );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected void doProgress()
  {
    long            millis = this.srcFile.lastModified();
    InputStream     in     = null;
    GZIPInputStream gzipIn = null;
    OutputStream    out    = null;
    String          msg    = null;
    try {
      in     = openInputFile( this.srcFile, null );
      gzipIn = new GZIPInputStream( in );
      out    = new BufferedOutputStream(
			new FileOutputStream( this.outFile ) );

      int b = gzipIn.read();
      while( b != -1 ) {
	out.write( b );
	b = gzipIn.read();
      }
      out.close();
      out = null;
      if( millis != -1 ) {
	this.outFile.setLastModified( millis );
      }
    }
    catch( InterruptedIOException ex ) {
      this.outFile.delete();
    }
    catch( Exception ex ) {
      this.outFile.delete();
      msg = ex.getMessage();
    }
    finally {
      EmuUtil.closeSilently( gzipIn );
      EmuUtil.closeSilently( in );
      EmuUtil.closeSilently( out );
    }
    if( msg != null ) {
      EmuUtil.fireShowErrorDlg( this.owner, msg, null );
    }
  }


	/* --- private Konstruktoren --- */

  private GZipUnpacker( Window owner, File srcFile, File outFile )
  {
    super(
	"JKCEMU gzip unpacker",
	"Entpacken von " + srcFile.getName() + "...",
	false,
	true,
	true );
    this.owner   = owner;
    this.srcFile = srcFile;
    this.outFile = outFile;
  }
}

/*
 * (c) 2008-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * GZip-Packer
 */

package jkcemu.file;

import java.awt.Frame;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.zip.GZIPOutputStream;
import jkcemu.base.EmuUtil;
import jkcemu.base.AbstractThreadFrm;


public class GZipPacker extends AbstractThreadFrm
{
  private Window owner;
  private File   srcFile;
  private File   outFile;


  public static void packFile( Window owner, File srcFile, File outFile )
  {
    Frame frm = new GZipPacker( owner, srcFile, outFile );
    frm.setTitle( "GZIP-Datei packen" );
    frm.setVisible( true );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected void doProgress()
  {
    long             millis = this.srcFile.lastModified();
    InputStream      in     = null;
    GZIPOutputStream out    = null;
    String           msg    = null;
    try {
      in  = openInputFile( this.srcFile, null );
      out = new GZIPOutputStream(
			new BufferedOutputStream(
				new FileOutputStream( outFile ) ) );


      int b = in.read();
      while( b != -1 ) {
	out.write( b );
	b = in.read();
      }
      out.finish();
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
      EmuUtil.closeSilently( in );
      EmuUtil.closeSilently( out );
    }
    if( msg != null ) {
      EmuUtil.fireShowErrorDlg( this.owner, msg, null );
    }
  }


	/* --- private Konstruktoren --- */

  private GZipPacker( Window owner, File srcFile, File outFile )
  {
    super(
	"JKCEMU gzip packer",
	"Packen von " + srcFile.getName() + "...",
	false,
	true,
	true );
    this.owner   = owner;
    this.srcFile = srcFile;
    this.outFile = outFile;
  }
}

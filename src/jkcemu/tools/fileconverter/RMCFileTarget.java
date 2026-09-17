/*
 * (c) 2025 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Konvertierung in eine RBASIC-Maschinencode-Datei
 */

package jkcemu.tools.fileconverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import jkcemu.base.EmuUtil;
import jkcemu.base.UserInputException;
import jkcemu.file.FileUtil;
import jkcemu.lang.LangUtil;


public class RMCFileTarget extends AbstractConvertTarget
{
  private byte[] dataBytes;
  private int    offs;
  private int    len;


  public RMCFileTarget(
		FileConvertFrm fileConvertFrm,
		byte[]         dataBytes,
		int            offs,
		int            len )
  {
    super(
		fileConvertFrm,
		LangUtil.getText( "fileconv.title.rbasic_machine_code" ) );
    this.dataBytes = dataBytes;
    this.offs      = offs;
    this.len       = len;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public javax.swing.filechooser.FileFilter getFileFilter()
  {
    return FileUtil.getRMCFileFilter();
  }


  @Override
  public File getSuggestedOutFile( File srcFile )
  {
    return FileUtil.replaceExtension( srcFile, ".rmc" );
  }


  @Override
  public String save( File file ) throws IOException, UserInputException
  {
    checkFileExtension( file, ".rmc" );
    int          begAddr   = this.fileConvertFrm.getBegAddr( true );
    int          endAddr   = begAddr + this.len - 1;
    int          startAddr = this.fileConvertFrm.getStartAddr( false );
    OutputStream out       = null;
    try {
      out = new FileOutputStream( file );
      out.write( 0xFE );
      out.write( begAddr );
      out.write( begAddr >> 8 );
      out.write( endAddr );
      out.write( endAddr >> 8 );
      if( startAddr >= 0 ) {
	out.write( startAddr );
	out.write( startAddr >> 8 );
      } else {
	out.write( begAddr );
	out.write( begAddr >> 8 );
      }
      out.write(
		this.dataBytes,
		this.offs,
		Math.min( this.dataBytes.length - this.offs, this.len ) );
      out.close();
      out = null;
    }
    finally {
      EmuUtil.closeSilently( out );
    }
    return null;
  }


  @Override
  public boolean usesBegAddr()
  {
    return true;
  }


  @Override
  public boolean usesStartAddr( int fileType )
  {
    return true;
  }
}

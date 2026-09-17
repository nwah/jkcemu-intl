/*
 * (c) 2015-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Konvertierung in eine TeleDisk-Datei
 */

package jkcemu.tools.fileconverter;

import java.io.File;
import java.io.IOException;
import jkcemu.disk.AbstractFloppyDisk;
import jkcemu.disk.TeleDisk;
import jkcemu.file.FileUtil;
import jkcemu.lang.LangUtil;


public class TeleDiskFileTarget extends AbstractConvertTarget
{
  private AbstractFloppyDisk disk;


  public TeleDiskFileTarget(
			FileConvertFrm     fileConvertFrm,
			AbstractFloppyDisk disk )
  {
    super(
		fileConvertFrm,
		LangUtil.getText( "fileconv.title.teledisk_file_td0" ) );
    this.disk = disk;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public int getMaxRemarkLength()
  {
    return 0x7E;
  }


  @Override
  public javax.swing.filechooser.FileFilter getFileFilter()
  {
    return FileUtil.getTeleDiskFileFilter();
  }


  @Override
  public File getSuggestedOutFile( File srcFile )
  {
    return FileUtil.replaceExtension( srcFile, ".td0" );
  }


  @Override
  public String save( File file ) throws IOException
  {
    checkFileExtension( file, ".td0" );
    return TeleDisk.export(
			this.disk,
			file,
			this.fileConvertFrm.getRemark() );
  }
}

/*
 * (c) 2015-2018 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Automatisches Laden von Dateien in den Arbeitsspeicher
 */

package jkcemu.base;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import jkcemu.Main;
import jkcemu.audio.AudioUtil;
import jkcemu.file.FileInfo;
import jkcemu.file.FileUtil;
import jkcemu.file.LoadData;
import jkcemu.lang.LangUtil;


public class AutoLoader extends Thread
{
  public static final String PROP_AUTOLOAD_PREFIX = "autoload.";

  private static final String TEXT_CANNOT_LOAD = "base.text.cannot_loaded";

  private EmuThread                     emuThread;
  private java.util.List<AutoLoadEntry> entries;


  public static void start( EmuThread emuThread, Properties props )
  {
    EmuSys emuSys = emuThread.getEmuSys();
    if( emuSys != null ) {
      java.util.List<AutoLoadEntry> entries = AutoLoadEntry.readEntries(
			props,
			emuSys.getPropPrefix() + PROP_AUTOLOAD_PREFIX );
      if( entries != null ) {
	if( !entries.isEmpty() ) {
	  (new AutoLoader( emuThread, entries )).start();
	}
      }
    }
  }


	/* --- Runnable --- */

  public void run()
  {
    try {
      for( AutoLoadEntry entry : this.entries ) {
	String fileName = entry.getFileName();
	if( fileName != null ) {
	  if( !fileName.isEmpty() ) {
	    this.emuThread.getScreenFrm().showStatusText(
					"AutoLoad: " + fileName );
	    try {
	      File file = new File( fileName );

	      // Dateityp ermitteln
	      if( AudioUtil.isAudioFile( file ) ) {
		throw new IOException( LangUtil.getText(
				"base.error.sound_file_not_supported" ) );
	      }
	      byte[] fileBuf  = FileUtil.readFile( file, true, 0x10000 );
	      if( fileBuf == null ) {
		throw new IOException( LangUtil.getText( TEXT_CANNOT_LOAD ) );
	      }
	      FileInfo fileInfo = FileInfo.analyzeFile( fileBuf, file );
	      if( fileInfo == null ) {
		throw new IOException(
			LangUtil.getText( "base.error.file_format_unknown" ) );
	      }
	      if( fileInfo.isTapeFile() ) {
		throw new IOException(
			LangUtil.getText( "base.error.tape_file_not_supported" ) );
	      }

	      // Ladeadresse ermitteln
	      Integer loadAddr = entry.getLoadAddr();
	      if( loadAddr == null ) {
		int begAddr = fileInfo.getBegAddr();
		if( begAddr >= 0 ) {
		  loadAddr = begAddr;
		}
	      }
	      if( loadAddr == null ) {
		EmuSys emuSys = emuThread.getEmuSys();
		if( emuSys != null ) {
		  loadAddr = emuSys.getLoadAddr();
		}
	      }
	      if( loadAddr == null ) {
		throw new IOException( LangUtil.getText(
				"base.error.load_address_not_specified" ) );
	      }
	      LoadData loadData = fileInfo.createLoadData( fileBuf );
	      if( loadData == null ) {
		throw new IOException( LangUtil.getText( TEXT_CANNOT_LOAD ) );
	      }
	      String msg = loadData.getInfoMsg();
	      if( msg != null ) {
		if( !msg.isEmpty() ) {
		  addMsg( fileName, msg );
		}
	      }
	      loadData.setBegAddr( loadAddr.intValue() );
	      loadData.setStartAddr( -1 );

	      // ggf. warten
	      int millis = entry.getMillisToWait();
	      if( millis > 0 ) {
		sleep( millis );
	      }

	      // Datei laden
	      emuThread.loadIntoMemory( loadData, null );
	    }
	    catch( IOException ex ) {
	      String msg = ex.getMessage();
	      if( msg != null ) {
		if( msg.isEmpty() ) {
		  msg = null;
		}
	      }
	      if( msg == null ) {
		msg = LangUtil.getText( TEXT_CANNOT_LOAD );
	      }
	      addMsg(
			fileName,
			msg != null ? msg : LangUtil.getText(
				TEXT_CANNOT_LOAD ) );
	    }
	  }
	}
      }
    }
    catch( InterruptedException ex ) {}
  }


	/* --- Konstruktor --- */

  private AutoLoader(
		EmuThread                     emuThread,
		java.util.List<AutoLoadEntry> entries )
  {
    super(
		Main.getThreadGroup(),
		LangUtil.getText( "base.title.jkcemu_auto_loader" ) );
    this.emuThread = emuThread;
    this.entries   = entries;
  }


	/* --- private Methoden --- */

  private void addMsg( String fileName, String msg )
  {
    this.emuThread.getScreenFrm().fireAppendMsg(
			"AutoLoad:\n" + fileName + ":\n" + msg );
  }
}

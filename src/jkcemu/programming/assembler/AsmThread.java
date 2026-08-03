/*
 * (c) 2012-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Assembler-Thread
 */

package jkcemu.programming.assembler;

import java.io.IOException;
import java.util.Collection;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.emusys.Z9001;
import jkcemu.file.FileFormat;
import jkcemu.programming.PrgLogger;
import jkcemu.programming.PrgOptions;
import jkcemu.programming.PrgSource;
import jkcemu.programming.PrgThread;
import jkcemu.text.EditText;


public class AsmThread extends PrgThread
{
  private Z80Assembler assembler;


  public AsmThread(
		EmuThread  emuThread,
		EditText   editText,
		PrgOptions options,
		Appendable logOut )
  {
    super( "JKCEMU assembler", emuThread, editText, options, logOut );
    this.assembler = new Z80Assembler(
				editText.getText(),
				null,
				editText.getFile(),
				options,
				PrgLogger.createLogger( logOut ),
				true );
  }


  public Collection<PrgSource> getPrgSources()
  {
    return this.assembler.getPrgSources();
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean execute() throws IOException
  {
    appendToLog( "Assembliere...\n" );
    boolean forZ9001 = false;
    if( this.emuThread != null ) {
      EmuSys emuSys = this.emuThread.getEmuSys();
      if( emuSys != null ) {
	forZ9001 = (emuSys instanceof Z9001 );
      }
    }
    boolean status = this.assembler.assemble( forZ9001 );
    if( status ) {
      if( this.options.getCreateAsmListing() ) {
	StringBuilder listing = this.assembler.getListing();
	if( listing != null ) {
	  fireOpenResultText( listing.toString(), null, null );
	}
      }
      if( this.options.getFormatSource() ) {
	String srcOut = this.assembler.getFormattedSourceText();
	if( srcOut != null ) {
	  fireReplaceSourceText( srcOut );
	}
      }
      if( this.options.getCodeToEmu() || this.options.getForceRun() ) {
	byte[] code = this.assembler.getCreatedCode();
	if( code != null ) {
	  writeCodeToEmu( this.assembler, FileFormat.BIN, true );
	}
      }
    }
    return status;
  }


  @Override
  public void cancel()
  {
    super.cancel();
    this.assembler.cancel();
  }
}

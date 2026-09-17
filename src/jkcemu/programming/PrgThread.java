/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Thread fuer einen Compiler/Assembler
 */

package jkcemu.programming;

import java.awt.Component;
import java.awt.EventQueue;
import java.io.IOException;
import java.util.Collection;
import jkcemu.Main;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.base.ErrorMsg;
import jkcemu.base.ScreenFrm;
import jkcemu.file.FileFormat;
import jkcemu.file.LoadData;
import jkcemu.lang.LangUtil;
import jkcemu.programming.assembler.AsmLabel;
import jkcemu.programming.assembler.Z80Assembler;
import jkcemu.text.EditText;
import jkcemu.text.TextEditFrm;
import jkcemu.tools.ReassFrm;
import jkcemu.tools.debugger.DebugFrm;


public abstract class PrgThread extends Thread
{
  protected EmuThread  emuThread;
  protected EditText   editText;
  protected PrgOptions options;

  private Appendable logOut;
  private boolean    execEnabled;


  public PrgThread(
		String     threadName,
		EmuThread  emuThread,
		EditText   editText,
		PrgOptions options,
		Appendable logOut )
  {
    super( Main.getThreadGroup(), threadName );
    this.emuThread   = emuThread;
    this.editText    = editText;
    this.options     = options;
    this.logOut      = logOut;
    this.execEnabled = true;
  }


  protected void appendToLog( String text )
  {
    if( text != null ) {
      try {
	this.logOut.append( text );
      }
      catch( IOException ex ) {}
    }
  }


  public void cancel()
  {
    this.execEnabled = false;
  }


  protected abstract boolean execute() throws IOException;


  protected void fireOpenResultText(
				final String                text,
				final Collection<PrgSource> prgSources,
				final PrgOptions            prgOptions )
  {
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    openResultText( text, prgSources, prgOptions );
		  }
		} );
  }


  protected void fireReplaceSourceText( final String text )
  {
    final EditText editText = this.editText;
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    editText.replaceText( text );
		  }
		} );
  }


  public EditText getEditText()
  {
    return this.editText;
  }


  protected void writeCodeToEmu(
			Z80Assembler assembler,
			FileFormat   fileFmt,
			boolean      logAddrs )
  {
    boolean forceRun  = this.options.getForceRun();
    byte[]  codeBytes = assembler.getCreatedCode();
    if( codeBytes != null ) {
      int     begAddr   = assembler.getBegAddr();
      int     startAddr = begAddr;
      Integer entryAddr = assembler.getEntryAddr();
      if( entryAddr != null ) {
	startAddr = entryAddr.intValue();
      }
      if( (this.emuThread != null)
	  && (begAddr >= 0) && (begAddr <= 0xFFFF) )
      {
	String secondSysName = null;
	EmuSys emuSys        = null;
	if( this.options.getCodeToSecondSystem() ) {
	  emuSys = this.emuThread.getEmuSys();
	  if( emuSys != null ) {
	    secondSysName = emuSys.getSecondSystemName();
	  }
	}
	appendToLog( LangUtil.getText(
			"programming.msg.loading_program_code_main" ) );
	try {
	  if( (emuSys != null) && (secondSysName != null) ) {
	    emuSys.loadIntoSecondSystem( codeBytes, begAddr );
	    appendToLog(
		String.format(
			"Programmcode in %s nach %04X-%04X geladen\n",
			secondSysName,
			begAddr,
			begAddr + codeBytes.length -  1) );
	    if( forceRun ) {
	      appendToLog( "\n" + secondSysName
		+ ": automatischer Programmstart nicht unterst\u00FCtzt\n" );
	    }
	  } else {
	    StringBuilder rvStatusMsg = new StringBuilder();
	    this.emuThread.loadIntoMemory(
		new LoadData(
			codeBytes,
			0,
			codeBytes.length,
			begAddr,
			forceRun ? startAddr : -1,
			fileFmt ),
		rvStatusMsg );
	    if( rvStatusMsg.length() > 0 ) {
	      String msg     = rvStatusMsg.toString();
	      String pattern = "Datei ";
	      if( msg.startsWith( pattern ) ) {
		appendToLog( "Programmcode "
				+ msg.substring( pattern.length() ) );
	      } else {
		appendToLog( msg );
	      }
	    } else if( logAddrs ) {
	      appendToLog(
		String.format(
			"Programmcode nach %04X-%04X geladen",
			begAddr,
			begAddr + codeBytes.length -  1) );
	    }
	    appendToLog( "\n" );
	    if( forceRun ) {
	      if( startAddr >= 0 ) {
		appendToLog(
			String.format(
				"Starte Programm auf Adresse %04X...\n",
				startAddr ) );
	      } else {
		appendToLog( LangUtil.getText(
				"programming.msg.starting_program_not_possible" ) );
	      }
	    }
	  }
	}
	catch( IOException ex ) {
	  appendToLog( LangUtil.getText(
				"programming.msg.loading_program_code_failed" ) );
	  String msg = ex.getMessage();
	  if( msg != null ) {
	    if( !msg.isEmpty() ) {
	      appendToLog( msg + "\n" );
	    }
	  }
	}
      }
      PrgOptions option = assembler.getOptions();
      if( options.getLabelsToDebugger() ) {
	labelsToDebugger( assembler, this.options.getCodeToSecondSystem() );
      }
      if( options.getLabelsToReassembler() ) {
	labelsToReass( assembler, this.options.getCodeToSecondSystem() );
      }
    } else {
      appendToLog( LangUtil.getText( "programming.msg.program_code_cannot_loaded" ) );
      if( forceRun ) {
	appendToLog( LangUtil.getText( "programming.msg.started" ) );
      }
      appendToLog( LangUtil.getText( "programming.msg.because_not_single" ) );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void run()
  {
    TextEditFrm textEditFrm = this.editText.getTextEditFrm();
    try {
      if( execute() ) {
	appendToLog( LangUtil.getText( "programming.msg.done" ) );
      }
    }
    catch( IOException ex ) {
      if( this.execEnabled ) {
	boolean done = false;
	String  msg  = ex.getMessage();
	if( msg != null ) {
	  if( !msg.isEmpty() ) {
	    appendToLog( LangUtil.getText( "programming.msg.error" ) );
	    appendToLog( msg );
	    if( !msg.endsWith( "\n" ) ) {
	      appendToLog( "\n" );
	    }
	    done = true;
	  }
	}
	if( !done ) {
	  appendToLog( LangUtil.getText( "programming.msg.i_o_error" ) );
	}
      } else {
	appendToLog( LangUtil.getText( "programming.msg.cancelled" ) );
      }
    }
    catch( Exception ex ) {
      if( this.execEnabled ) {
	ErrorMsg.showLater( textEditFrm, ex );
      }
    }
    if( textEditFrm != null ) {
      textEditFrm.threadTerminated( this );
    }
  }


	/* --- private Methoden --- */

  private void labelsToDebugger( Z80Assembler assembler, boolean secondSys )
  {
    AsmLabel[] labels = assembler.getSortedLabels();
    if( labels != null ) {
      if( labels.length > 0 ) {
	ScreenFrm screenFrm = Main.getScreenFrm();
	if( screenFrm != null ) {
	  boolean  done     = false;
	  boolean  updOnly  = this.options.getLabelsUpdateBreakpointsOnly();
	  DebugFrm debugFrm = null;
	  if( secondSys && (this.emuThread != null) ) {
	    EmuSys emuSys = this.emuThread.getEmuSys();
	    if( emuSys != null ) {
	      if( emuSys.getSecondSystemName() != null ) {
		if( updOnly ) {
		  debugFrm = screenFrm.getSecondDebugger();
		} else {
		  debugFrm = screenFrm.openSecondDebugger();
		}
		done = true;
	      }
	    }
	  }
	  if( !done ) {
	    if( updOnly ) {
	      debugFrm = screenFrm.getPrimaryDebugger();
	    } else {
	      debugFrm = screenFrm.openPrimaryDebugger();
	    }
	  }
	  if( debugFrm != null ) {
	    debugFrm.setLabels(
			labels,
			this.options.getLabelsCaseSensitive(),
			updOnly );
	  }
	}
      }
    }
  }


  private void labelsToReass( Z80Assembler assembler, boolean secondSys )
  {
    AsmLabel[] labels = assembler.getSortedLabels();
    if( labels != null ) {
      if( labels.length > 0 ) {
	ScreenFrm screenFrm = Main.getScreenFrm();
	if( screenFrm != null ) {
	  boolean  done     = false;
	  ReassFrm reassFrm = null;
	  if( secondSys && (this.emuThread != null) ) {
	    EmuSys emuSys = this.emuThread.getEmuSys();
	    if( emuSys != null ) {
	      if( emuSys.getSecondSystemName() != null ) {
		reassFrm = screenFrm.openSecondReassembler();
		done     = true;
	      }
	    }
	  }
	  if( !done ) {
	    reassFrm = screenFrm.openPrimaryReassembler();
	  }
	  if( reassFrm != null ) {
	    reassFrm.setLabels(
			labels,
			assembler.getBegAddr(),
			assembler.getEndAddr() );
	  }
	}
      }
    }
  }


  private void openResultText(
			String                text,
			Collection<PrgSource> prgSources,
			PrgOptions            prgOptions )
  {
    TextEditFrm textEditFrm = this.editText.getTextEditFrm();
    if( textEditFrm != null ) {
      EditText editText = this.editText.getResultEditText();
      if( editText != null ) {
	if( editText.hasDataChanged()
	    || !textEditFrm.contains( editText ) )
	{
	  editText = null;
	}
      }
      if( editText != null ) {
	editText.setText( text );
	if( prgOptions != null ) {
	  editText.setPrgOptions( prgOptions );
	}
	Component tab = editText.getTab();
	if( tab != null ) {
	  textEditFrm.setSelectedTab( tab );
	}
      } else {
	editText = textEditFrm.openText( text );
	if( prgOptions != null ) {
	  editText.setPrgOptions( prgOptions );
	}
	this.editText.setResultEditText( editText );
      }
      if( prgSources != null ) {
	textEditFrm.setLineAddrsIfEnabled( prgSources );
      }
    }
  }
}

/*
 * (c) 2012-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * BASIC-Compiler-Thread
 */

package jkcemu.programming.basic;

import java.io.IOException;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.lang.LangUtil;
import jkcemu.programming.PrgLogger;
import jkcemu.programming.PrgThread;
import jkcemu.programming.assembler.Z80Assembler;
import jkcemu.programming.basic.target.Z9001KRTTarget;
import jkcemu.programming.basic.target.Z9001Target;
import jkcemu.text.EditText;


public class BasicCompilerThread extends PrgThread
{
  private String        sysTitle;
  private PrgLogger     logger;
  private BasicCompiler compiler;
  private BasicOptions  basicOptions;


  public BasicCompilerThread(
			EmuThread    emuThread,
			EditText     editText,
			BasicOptions options,
			Appendable   logOut )
  {
    super( LangUtil.getText(
			"basic.title.jkcemu_basic_compiler" ),
		emuThread,
		editText,
		options,
		logOut );

    EmuSys emuSys = (emuThread != null ? emuThread.getEmuSys() : null);
    if( emuSys != null ) {
      this.sysTitle = emuSys.getTitle();
    } else {
      this.sysTitle = null;
    }
    this.basicOptions = options;
    this.logger       = PrgLogger.createLogger( logOut );
    this.compiler     = new BasicCompiler(
				editText.getText(),
				editText.getFile(),
				options,
				this.logger );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean execute() throws IOException
  {
    boolean status = false;
    appendToLog( LangUtil.getText( "basic.msg.compiling" ) );

    AbstractTarget target = this.basicOptions.getTarget();
    if( target != null ) {
      String text = target.toString();
      if( text != null ) {
	appendToLog( LangUtil.getText( "basic.msg.target_system" ) );
	appendToLog( text );
	appendToLog( "\n" );
      }
      final String asmText = this.compiler.compile();
      if( asmText != null ) {
	appendToLog( LangUtil.getText( "programming.msg.assembling" ) );
	Z80Assembler assembler = new Z80Assembler(
						asmText,
						"Assembler-Quelltext",
						null,
						this.options,
						this.logger,
						true );
	status = assembler.assemble(
			(target instanceof Z9001Target)
				|| (target instanceof Z9001KRTTarget) );
	if( (this.basicOptions.getBssBegAddr() >= 0)
	    && assembler.getOrgOverlapped() )
	{
	  appendToLog( LangUtil.getText(
				"basic.msg.program_code_range" ) );
	}
	if( assembler.getRelJumpsTooLong() ) {
	  appendToLog( LangUtil.getText( "basic.msg.please_compile_option" ) );
	}
	if( status ) {
	  byte[] code = assembler.getCreatedCode();
	  if( code != null ) {
	    if( code.length > 0 ) {
	      int codeBegAddr = this.basicOptions.getCodeBegAddr();
	      appendToLog( LangUtil.getText(
				"basic.msg.memory_usage" ) );
	      appendToLog( String.format(
				"  %04X-%04X: Programmcode\n",
				codeBegAddr,
				codeBegAddr + code.length - 1 ) );
	      Integer topAddr = assembler.getLabelValue(
						BasicCompiler.TOP_LABEL );
	      if( topAddr != null ) {
		int bssBegAddr = this.basicOptions.getBssBegAddr();
		if( bssBegAddr < 0 ) {
		  bssBegAddr = this.basicOptions.getCodeBegAddr() + code.length;
		}
		if( (bssBegAddr >= 0) && (bssBegAddr < topAddr.intValue()) ) {
		  appendToLog( String.format(
				"  %04X-%04X: Variablen, Speicherzellen%s\n",
				bssBegAddr,
				topAddr.intValue() - 1,
				this.basicOptions.getStackSize() > 0 ?
					", Stack" : "" ) );
		}
	      }
	      if( this.options.getCodeToEmu()
		  || this.options.getForceRun() )
	      {
		writeCodeToEmu(
			assembler,
			target.getDefaultFileFormat(),
			false );
		if( !this.basicOptions.isAppTypeSubroutine()
		    && !this.options.getForceRun()
		    && (this.emuThread != null) )
		{
		  EmuSys emuSys = this.emuThread.getEmuSys();
		  if( emuSys != null ) {
		    String startCmd = target.getStartCmd(
					emuSys,
					this.basicOptions.getAppName(),
					this.basicOptions.getCodeBegAddr() );
		    if( startCmd != null ) {
		      if( !startCmd.isEmpty() ) {
			appendToLog(
				LangUtil.getText(
					"basic.msg.command_start_program" ) );
			appendToLog( startCmd );
			appendToLog( "\n" );
		      }
		    }
		  }
		}
	      }
	    }
	  }
	}
	if( this.basicOptions.getShowAssemblerText() ) {
	  fireOpenResultText(
			asmText,
			assembler.getPrgSources(),
			this.basicOptions );
	}
      }
    } else {
      if( this.sysTitle != null ) {
	appendToLog( LangUtil.getText(
			"basic.msg.error_target_system" ) );
	appendToLog( this.sysTitle );
	appendToLog( LangUtil.getText( "basic.msg.not_supported" ) );
      } else {
	appendToLog( LangUtil.getText(
			"basic.msg.error_target_system_unknown" ) );
      }
    }
    return status;
  }


  @Override
  public void cancel()
  {
    super.cancel();
    this.compiler.cancel();
  }
}

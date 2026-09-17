/*
 * (c) 2016-2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponente fuer die Emulation des Lautsprechers
 * bzw. Sound-Generatorausgangs
 */

package jkcemu.audio;

import java.io.File;
import java.io.IOException;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileUtil;
import jkcemu.file.RecentDirsMngr;
import jkcemu.lang.LangUtil;


public abstract class AbstractAudioOutFld extends AbstractAudioIOFld
{
  protected AudioOut          audioOut;
  protected AudioOut          recordedData;
  protected boolean           recordedDataSaved;
  protected javax.swing.Timer durationTimer;
  protected JLabel            labelDuration;
  protected JTextField        fldDuration;
  protected JButton           btnPlay;
  protected JButton           btnSave;


  public AbstractAudioOutFld( AudioFrm audioFrm, EmuThread emuThread )
  {
    super( audioFrm, emuThread );
    this.audioOut      = null;
    this.recordedData  = null;
    this.durationTimer = new javax.swing.Timer( 500, this );
    this.durationTimer.setRepeats( true );
  }


  protected boolean checkRecordedDataSaved()
  {
    boolean rv = true;
    if( (this.recordedData != null) && !this.recordedDataSaved ) {
      if( BaseDlg.showYesNoWarningDlg(
		this,
		LangUtil.getText( "audio.msg.recording_not_saved_yet_want" ),
		LangUtil.getText( "common.msg.warning" ) ) )
      {
	this.recordedData = null;
      } else {
	rv = false;
      }
    }
    return rv;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( ActionEvent e )
  {
    boolean rv  = true;
    Object  src = e.getSource();
    if( src == this.durationTimer ) {
      updDurationText();
    } else if( src == this.btnPlay ) {
      doPlay();
    } else if( src == this.btnSave ) {
      doSave();
    } else {
      rv = super.doAction( e );
    }
    return rv;
  }


  @Override
  protected void doDisable()
  {
    AudioOut audioOut = this.audioOut;
    if( audioOut != null ) {
      this.audioOut = null;
      audioOut.fireStop();
      this.durationTimer.stop();
      if( audioOut.hasRecordedData() ) {
	this.btnPlay.setEnabled( true );
	this.btnSave.setEnabled( true );
	this.labelDuration.setEnabled( true );
	this.fldDuration.setEnabled( true );
	this.fldDuration.setText(
		AudioUtil.getDurationText(
				audioOut.getFrameRate(),
				audioOut.getRecordedFrameCount() ) );
	this.recordedData      = audioOut;
	this.recordedDataSaved = false;
      } else {
	this.recordedData = null;
      }
      updFieldsEnabled();
    }
  }


	/* --- private Methoden --- */

  private void doPlay()
  {
    AudioOut audioOut = this.recordedData;
    if( audioOut != null ) {
      try {
	AudioPlayFrm.open(
		audioOut.createPCMDataSourceOfRecordedData(),
		"Wiedergabe der Aufnahme..." );
      }
      catch( IOException ex ) {
	BaseDlg.showErrorDlg( this, ex );
      }
    }
  }


  private void doSave()
  {
    AudioOut audioOut = this.recordedData;
    if( audioOut != null ) {
      javax.swing.filechooser.FileFilter[] fileFilters = null;
      if( (audioOut.getSampleSizeInBits() > 1)
	  || (audioOut.getChannels() > 1) )
      {
	fileFilters = new javax.swing.filechooser.FileFilter[] {
					AudioFile.getFileFilter() };
      } else {
	fileFilters = new javax.swing.filechooser.FileFilter[] {
					AudioFile.getFileFilter(),
					CSWFile.getFileFilter() };
      }
      File file = FileUtil.showFileSaveDlg(
			this.audioFrm,
			LangUtil.getText( "audio.title.save_sound_tape" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_AUDIO ),
			fileFilters );
      if( file != null ) {
	boolean csw   = false;
	boolean tzx   = false;
	String  fName = file.getName();
	if( fName != null ) {
	  fName = fName.toLowerCase();
	  csw   = (fName.endsWith( ".csw" ) || fName.endsWith( ".csw1" ));
	  tzx   = (fName.endsWith( ".cdt" ) || fName.endsWith( ".tzx" ));
	}
	PCMDataSource pcm = null;
	try {
	  pcm = audioOut.createPCMDataSourceOfRecordedData();
	  if( csw ) {
	    CSWFile.write( pcm, file );
	  } else if( tzx ) {
	    TZXFile.write( pcm, file );
	  } else {
	    AudioFile.write( pcm, file );
	  }
	  RecentDirsMngr.setRecentDir( file, RecentDirsMngr.FILE_CAT_AUDIO );
	  this.recordedDataSaved = true;
	}
	catch( IOException ex ) {
	  BaseDlg.showErrorDlg( this, ex );
	}
	finally {
	  EmuUtil.closeSilently( pcm );
	}
      }
    }
  }


  private void updDurationText()
  {
    String   text     = "";
    AudioOut audioOut = this.audioOut;
    if( audioOut == null ) {
      audioOut = this.recordedData;
    }
    if( audioOut != null ) {
      text = audioOut.getDurationText();
    }
    this.fldDuration.setText( text );
  }
}

/*
 * (c) 2018-2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponenete fuer einen USB-Anschluss
 */

package jkcemu.usb;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import jkcemu.Main;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.file.DirSelectDlg;
import jkcemu.file.FileNameFld;
import jkcemu.file.FileUtil;
import jkcemu.file.RecentDirsMngr;
import jkcemu.lang.LangUtil;


public class USBInterfaceFld
			extends JPanel
			implements
				ActionListener,
				DropTargetListener
{
  private USBInterfaceFrm owner;
  private VDIP            vdip;
  private FileNameFld     dirFld;
  private JButton         btnDirSelect;
  private JButton         btnDirRemove;
  private JCheckBox       cbFileNameMapper;
  private JCheckBox       cbReadOnly;
  private JCheckBox       cbForceLowerCase;
  private JCheckBox       cbForceCurTimestamp;
  private DropTarget      dropTarget;


  public USBInterfaceFld(
		USBInterfaceFrm owner,
		VDIP            vdip )
  {
    this.owner = owner;
    this.vdip  = vdip;

    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					1.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    add(
	GUIFactory.createLabel(
		LangUtil.getText( "usb.label.directory_emulated_usb" ) ),
	gbc );

    this.dirFld   = new FileNameFld();
    gbc.fill      = GridBagConstraints.HORIZONTAL;
    gbc.weightx   = 1.0;
    gbc.gridwidth = 1;
    gbc.gridy++;
    add( this.dirFld, gbc );

    this.btnDirSelect = GUIFactory.createRelImageResourceButton(
					this,
					"file/open.png",
					LangUtil.getText(
						EmuUtil.TEXT_SELECT_DIR ) );
    gbc.fill        = GridBagConstraints.NONE;
    gbc.weightx     = 0.0;
    gbc.insets.left = 0;
    gbc.gridx++;
    add( this.btnDirSelect, gbc );

    this.btnDirRemove = GUIFactory.createRelImageResourceButton(
					this,
					"file/delete.png",
					LangUtil.getText( "usb.action.remove_directory" ) );
    gbc.gridx++;
    add( this.btnDirRemove, gbc );

    this.cbFileNameMapper = GUIFactory.createCheckBox(
		LangUtil.getText( "usb.option.shorten_long_file" ) );
    gbc.insets.left = 5;
    gbc.gridwidth   = GridBagConstraints.REMAINDER;
    gbc.gridx       = 0;
    gbc.gridy++;
    add( this.cbFileNameMapper, gbc );

    this.cbReadOnly = GUIFactory.createCheckBox(
				LangUtil.getText( "common.option.write_protection_read" ),
				true );
    gbc.insets.top = 0;
    gbc.gridy++;
    add( this.cbReadOnly, gbc );

    this.cbForceLowerCase = GUIFactory.createCheckBox(
					LangUtil.getText( "common.option.write_file_names" ) );
    gbc.gridy++;
    add( this.cbForceLowerCase, gbc );

    this.cbForceCurTimestamp = GUIFactory.createCheckBox(
			LangUtil.getText( "usb.option.always_use_current" ) );
    gbc.insets.bottom = 5;
    gbc.gridy++;
    add( this.cbForceCurTimestamp, gbc );


    // Sonstiges
    this.dropTarget = new DropTarget( this.dirFld, this );
    updFields();
  }


  public void updFields()
  {
    this.dirFld.setFile( this.vdip.getDirectory() );

    this.cbFileNameMapper.setSelected(
			this.vdip.getFileNameMapperEnabled() );

    this.cbReadOnly.setSelected(
			this.vdip.getReadOnly() );

    this.cbForceCurTimestamp.setSelected(
			this.vdip.getForceCurrentTimestamp() );

    this.cbForceLowerCase.setSelected(
			this.vdip.getForceLowerCaseFileNames() );

    updFieldsEnabled();
  }


	/* --- DropTargetListener --- */

  @Override
  public void dragEnter( DropTargetDragEvent e )
  {
    if( !FileUtil.isFileDrop( e ) )
      e.rejectDrag();
  }


  @Override
  public void dragExit( DropTargetEvent e )
  {
    // leer
  }


  @Override
  public void dragOver( DropTargetDragEvent e )
  {
    // leer
  }


  @Override
  public void drop( DropTargetDropEvent e )
  {
    File file = FileUtil.fileDrop( this, e );
    if( file != null ) {
      setDirFile( file );
    }
  }


  @Override
  public void dropActionChanged( DropTargetDragEvent e )
  {
    // leer
  }


	/* --- ActionsListener --- */

  @Override
  public void actionPerformed( ActionEvent e )
  {
    Object src = e.getSource();
    if( src == this.btnDirSelect ) {
      doDirSelect();
    }
    else if( src == this.btnDirRemove ) {
      setDirFile( null );
    }
    else if( src == this.cbFileNameMapper ) {
      setFileNameMapperEnabled( this.cbFileNameMapper.isSelected() );
    }
    else if( src == this.cbReadOnly ) {
      setReadOnly( this.cbReadOnly.isSelected() );
    }
    else if( src == this.cbForceLowerCase ) {
      setForceLowerCase( this.cbForceLowerCase.isSelected() );
    }
    else if( src == this.cbForceCurTimestamp ) {
      setForceCurrentTimestamp( this.cbForceCurTimestamp.isSelected() );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    this.btnDirSelect.addActionListener( this );
    this.btnDirRemove.addActionListener( this );
    this.cbFileNameMapper.addActionListener( this );
    this.cbReadOnly.addActionListener( this );
    this.cbForceLowerCase.addActionListener( this );
    this.cbForceCurTimestamp.addActionListener( this );
    this.dropTarget.setActive( true );
  }


  @Override
  public void removeNotify()
  {
    this.btnDirSelect.removeActionListener( this );
    this.btnDirRemove.removeActionListener( this );
    this.cbFileNameMapper.removeActionListener( this );
    this.cbReadOnly.removeActionListener( this );
    this.cbForceLowerCase.removeActionListener( this );
    this.cbForceCurTimestamp.removeActionListener( this );
    this.dropTarget.setActive( false );
    super.removeNotify();
  }


	/* --- private Methoden --- */

  private void doDirSelect()
  {
    File lastDir = this.dirFld.getFile();
    if( lastDir == null ) {
      lastDir = RecentDirsMngr.getRecentDir( RecentDirsMngr.FILE_CAT_USB );
    }
    if( lastDir != null ) {
      if( !lastDir.isDirectory() ) {
	lastDir = lastDir.getParentFile();
      }
    }
    File dirFile = DirSelectDlg.selectDirectory( this.owner, lastDir );
    if( dirFile != null ) {
      setDirFile( dirFile );
    }
  }


  private void setDirFile( File dirFile )
  {
    boolean done = false;
    if( dirFile != null ) {
      if( dirFile.isDirectory() ) {
	this.dirFld.setFile( dirFile );
	this.vdip.setDirectory( dirFile );
	Main.setProperty(
		this.vdip.getPropNameDirectory(),
		dirFile.getPath() );
	RecentDirsMngr.setRecentDir( dirFile, RecentDirsMngr.FILE_CAT_USB );
	done = true;
      }
    }
    if( !done ) {
      this.dirFld.setFile( null );
      this.vdip.setDirectory( null );
      Main.setProperty( this.vdip.getPropNameDirectory(), "" );
    }
    updFieldsEnabled();
  }


  private void setFileNameMapperEnabled( boolean state )
  {
    this.vdip.setFileNameMapperEnabled( state );
    Main.setProperty(
		this.vdip.getPropNameFileNameMapperEnabled(),
		Boolean.toString( state ) );
  }


  private void setForceCurrentTimestamp( boolean state )
  {
    this.vdip.setForceCurrentTimestamp( state );
    Main.setProperty(
		this.vdip.getPropNameForceCurrentTimestamp(),
		Boolean.toString( state ) );
  }


  private void setForceLowerCase( boolean state )
  {
    this.vdip.setForceLowerCaseFileNames( state );
    Main.setProperty(
		this.vdip.getPropNameForceLowerCaseFileNames(),
		Boolean.toString( state ) );
  }


  private void setReadOnly( boolean state )
  {
    this.vdip.setReadOnly( state );
    Main.setProperty(
		this.vdip.getPropNameReadOnly(),
		Boolean.toString( state ) );
    this.cbForceCurTimestamp.setEnabled( !state );
    this.cbForceLowerCase.setEnabled( !state );
  }


  private void updFieldsEnabled()
  {
    boolean state = (this.vdip.getDirectory() != null);
    this.btnDirRemove.setEnabled( state );
    this.cbFileNameMapper.setEnabled( state );
    this.cbReadOnly.setEnabled( state );

    if( this.vdip.getReadOnly() ) {
      state = false;
    }
    this.cbForceCurTimestamp.setEnabled( state );
    this.cbForceLowerCase.setEnabled( state );
  }
}

/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog zur Eingabe des Diskettenformats
 */

package jkcemu.disk;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.EventObject;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import jkcemu.base.BaseDlg;
import jkcemu.base.GUIFactory;


public class FloppyDiskFormatDlg extends BaseDlg
{
  public static enum Flag {
			READONLY,
			FULL_FORMAT,
			PHYS_FORMAT,
			AUTO_REFRESH,
			FORCE_LOWERCASE };

  private static boolean lastForceLowerCase = true;

  private boolean                   approved;
  private boolean                   notified;
  private FloppyDiskFormat          selectedFmt;
  private FloppyDiskFormatSelectFld fmtSelectFld;
  private JComboBox<Object>         comboFmt;
  private JCheckBox                 cbReadOnly;
  private JCheckBox                 cbAutoRefresh;
  private JCheckBox                 cbForceLowerCase;
  private JButton                   btnOK;
  private JButton                   btnCancel;


  public FloppyDiskFormatDlg(
			Window           owner,
			boolean          withHDformats,
			FloppyDiskFormat preSelFmt,
			Flag...          flags )
  {
    super( owner, "Datei laden" );
    setTitle( "JKCEMU Diskettenformat" );
    this.approved    = false;
    this.notified    = false;
    this.selectedFmt = null;


    // Fensterinhalt
    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					0.0, 0.0,
					GridBagConstraints.CENTER,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    // vollstaendiges Format
    if( containsFlag( flags, Flag.FULL_FORMAT ) ) {
      this.fmtSelectFld = new FloppyDiskFormatSelectFld( false );
      this.fmtSelectFld.setFormat( preSelFmt );
      add( this.fmtSelectFld, gbc );
      gbc.gridy++;
    } else {
      this.fmtSelectFld = null;
    }

    // Formatauswahl
    if( containsFlag( flags, Flag.PHYS_FORMAT ) ) {
      this.comboFmt = GUIFactory.createComboBox();
      this.comboFmt.setEditable( false );
      if( preSelFmt == null ) {
	this.comboFmt.addItem( "--- Bitte ausw\u00E4hlen ---" );
      }
      FloppyDiskFormat[] formats = FloppyDiskFormat.getFormats();
      if( formats != null ) {
	boolean state = false;
	for( int i = 0; i < formats.length; i++ ) {
	  FloppyDiskFormat fmt = formats[ i ];
	  if( (withHDformats || !fmt.isHD())
	      && (fmt.getSides() == 2)
	      && (fmt.getCylinders() == 80)
	      && (fmt.getSectorSize() >= 512) )
	  {
	    this.comboFmt.addItem( fmt );
	    state = true;
	  }
	}
	for( int i = 0; i < formats.length; i++ ) {
	  FloppyDiskFormat fmt = formats[ i ];
	  if( (withHDformats || !fmt.isHD())
	      && ((fmt.getSides() != 2)
			|| (fmt.getCylinders() != 80)
			|| (fmt.getSectorSize() < 512)) )
	  {
	    if( state ) {
	      this.comboFmt.addItem( "--- \u00C4ltere Formate ---" );
	      state = false;
	    }
	    this.comboFmt.addItem( fmt );
	  }
	}
      }
      if( preSelFmt != null ) {
	this.comboFmt.setSelectedItem( preSelFmt );
      }
      add( this.comboFmt, gbc );
      gbc.gridy++;
    } else {
      this.comboFmt = null;
    }

    // Checkboxen
    java.util.List<JCheckBox> checkBoxes = null;
    this.cbReadOnly                      = null;
    this.cbAutoRefresh                   = null;
    this.cbForceLowerCase                = null;
    if( flags != null ) {
      checkBoxes = new ArrayList<>( 4 );
      for( int i = 0; i < flags.length; i++ ) {
	if( flags[ i ] != null ) {
	  switch( flags[ i ] ) {
	    case READONLY:
	      this.cbReadOnly = GUIFactory.createCheckBox(
					"Schreibschutz (Nur-Lese-Modus)",
					true );
	      checkBoxes.add( this.cbReadOnly );
	      break;

	    case AUTO_REFRESH:
	      this.cbAutoRefresh = GUIFactory.createCheckBox(
					"Automatisch aktualisieren",
					false );
	      checkBoxes.add( this.cbAutoRefresh );
	      break;

	    case FORCE_LOWERCASE:
	      this.cbForceLowerCase = GUIFactory.createCheckBox(
					"Dateinamen klein schreiben",
					lastForceLowerCase );
	      checkBoxes.add( this.cbForceLowerCase );
	      break;
	  }
	}
      }
    }
    if( checkBoxes != null ) {
      int n = checkBoxes.size();
      if( n > 0 ) {
	if( n > 1 ) {
	  gbc.anchor = GridBagConstraints.WEST;
	} else {
	  gbc.anchor = GridBagConstraints.CENTER;
	}
	gbc.insets.bottom = 0;
	gbc.gridwidth     = GridBagConstraints.REMAINDER;
	for( int i = 0; i < n; i++ ) {
	  if( i == 1 ) {
	    gbc.insets.top = 0;
	  }
	  if( i == (n - 1) ) {
	    gbc.insets.bottom = 5;
	  }
	  add( checkBoxes.get( i ), gbc );
	  gbc.gridy++;
	}
      }
    }
    updReadOnlyDependingFlds();

    // Knoepfe
    JPanel panelBtns = GUIFactory.createPanel(
					new GridLayout( 1, 2, 5, 5 ) );
    gbc.anchor        = GridBagConstraints.CENTER;
    gbc.fill          = GridBagConstraints.NONE;
    gbc.insets.top    = 10;
    gbc.insets.bottom = 10;
    gbc.gridwidth     = GridBagConstraints.REMAINDER;
    add( panelBtns, gbc );

    this.btnOK = GUIFactory.createButtonOK();
    panelBtns.add( this.btnOK );

    this.btnCancel = GUIFactory.createButtonCancel();
    panelBtns.add( this.btnCancel );


    // Fenstergroesse und -position
    pack();
    setParentCentered();
    setResizable( true );
  }


  public boolean getAutoRefresh()
  {
    boolean state = false;
    if( this.cbAutoRefresh != null ) {
      state = this.cbAutoRefresh.isSelected();
    }
    return state;
  }


  public boolean getForceLowerCase()
  {
    return this.cbForceLowerCase != null ?
				this.cbForceLowerCase.isSelected()
				: false;
  }


  public FloppyDiskFormat getFormat()
  {
    return this.selectedFmt;
  }


  public boolean getReadOnly()
  {
    return this.cbReadOnly != null ? this.cbReadOnly.isSelected() : false;
  }


  public void setAutoRefresh( boolean state )
  {
    if( this.cbAutoRefresh != null )
      this.cbAutoRefresh.setSelected( state );
  }


  public void setForceLowerCase( boolean state )
  {
    if( this.cbForceLowerCase != null )
      this.cbForceLowerCase.setSelected( state );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    if( !this.notified ) {
      this.notified = true;
      if( this.cbReadOnly != null ) {
	this.cbReadOnly.addActionListener( this );
      }
      this.btnOK.addActionListener( this );
      this.btnCancel.addActionListener( this );
    }
  }


  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src != null ) {
      if( src == this.btnOK ) {
	rv = true;
	if( this.fmtSelectFld != null ) {
	  this.selectedFmt = this.fmtSelectFld.getFormat();
	  this.approved    = true;
	} else if( this.comboFmt != null ) {
	  Object value = this.comboFmt.getSelectedItem();
	  if( value != null ) {
	    if( value instanceof FloppyDiskFormat ) {
	      this.selectedFmt = (FloppyDiskFormat) value;
	      this.approved    = true;
	    }
	  }
	} else {
	  this.approved = true;
	}
	if( this.approved ) {
	  if( this.cbForceLowerCase != null ) {
	    lastForceLowerCase = this.cbForceLowerCase.isSelected();
	  }
	  doClose();
	}
      }
      else if( src == this.btnCancel ) {
	rv = true;
	doClose();
      }
      else if( src == this.cbReadOnly ) {
	rv = true;
	updReadOnlyDependingFlds();
      }
    }
    return rv;
  }


  @Override
  public void removeNotify()
  {
    super.removeNotify();
    if( this.notified ) {
      this.notified = false;
      if( this.cbReadOnly != null ) {
	this.cbReadOnly.removeActionListener( this );
      }
      this.btnOK.removeActionListener( this );
      this.btnCancel.removeActionListener( this );
    }
  }


  /*
   * Wenn bereits eine Format vorausgewaehlt ist,
   * wird der Focus auf den OK-Knopf gesetzt,
   * damit man einfach Enter druecken kann.
   */
  @Override
  public void windowOpened( WindowEvent e )
  {
    if( (e.getWindow() == this)
	&& (this.comboFmt != null)
	&& (this.btnOK != null) )
    {
      Object o = this.comboFmt.getSelectedItem();
      if( o != null ) {
	if( o instanceof FloppyDiskFormat ) {
	  this.btnOK.requestFocus();
	}
      }
    }
  }


	/* --- private Methoden --- */

  private static boolean containsFlag( Flag[] flags, Flag flag )
  {
    boolean rv = false;
    if( (flags != null) && (flag != null) ) {
      for( int i = 0; i < flags.length; i++ ) {
	if( flags[ i ] != null ) {
	  if( flags[ i ].equals( flag ) ) {
	    rv = true;
	    break;
	  }
	}
      }
    }
    return rv;
  }


  private void updReadOnlyDependingFlds()
  {
    if( (this.cbReadOnly != null) && (this.cbForceLowerCase != null) ) {
      this.cbForceLowerCase.setEnabled( !this.cbReadOnly.isSelected() );
    }
  }
}

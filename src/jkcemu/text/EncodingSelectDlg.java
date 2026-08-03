/*
 * (c) 2008-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog zur Auswahl eines Zeichensatzes und weiterer Optionen
 */

package jkcemu.text;

import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.EventObject;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import jkcemu.base.BaseDlg;
import jkcemu.base.GUIFactory;
import jkcemu.file.FileUtil;


public class EncodingSelectDlg extends BaseDlg
{
  private boolean           applied;
  private boolean           notified;
  private boolean           ignoreEofByte;
  private CharConverter     charConverter;
  private String            encodingName;
  private String            encodingDisplayText;
  private String            nativeEncodingItem;
  private String            nativeEncodingName;
  private JComboBox<Object> comboEncoding;
  private JCheckBox         cbIgnoreEofByte;
  private JButton           btnOK;
  private JButton           btnCancel;


  public EncodingSelectDlg(
			Frame  parent,
			String presetEncoding )
  {
    super( parent, "Zeichensatz ausw\u00E4hlen" );
    this.applied             = false;
    this.notified            = false;
    this.ignoreEofByte       = false;
    this.charConverter       = null;
    this.encodingName        = null;
    this.encodingDisplayText = null;
    this.nativeEncodingItem  = null;
    this.nativeEncodingName  = null;


    // Systemzeichensatz
    this.nativeEncodingName = FileUtil.getNativeFileEncodingName();
    if( this.nativeEncodingName != null ) {
      this.nativeEncodingItem = "Systemzeichensatz ("
					+ this.nativeEncodingName
					+ ")";
    }


    // Fensterinhalt
    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					2, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    // Fragetext
    add(
	GUIFactory.createLabel(
		"Mit welchem Zeichensatz soll die Datei"
			+ " ge\u00F6ffnet werden?" ),
	gbc );


    // Auswahlfeld
    this.comboEncoding = GUIFactory.createComboBox();
    if( this.nativeEncodingItem != null ) {
      this.comboEncoding.addItem( this.nativeEncodingItem );
    }
    this.comboEncoding.addItem(
                new CharConverter( CharConverter.Encoding.ASCII ) );
    this.comboEncoding.addItem(
                new CharConverter( CharConverter.Encoding.ISO646DE ) );
    this.comboEncoding.addItem(
		new CharConverter( CharConverter.Encoding.CP437 ) );
    this.comboEncoding.addItem(
		new CharConverter( CharConverter.Encoding.CP850 ) );
    this.comboEncoding.addItem( new CharConverter(
		CharConverter.Encoding.LATIN1 ) );
    this.comboEncoding.addItem( "UTF-8" );
    this.comboEncoding.addItem( "UTF-16BE (Big Endian)" );
    this.comboEncoding.addItem( "UTF-16LE (Little Endian)" );
    this.comboEncoding.setEditable( false );
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.gridy++;
    add( this.comboEncoding, gbc );


    // Voreinstellung
    if( presetEncoding != null ) {
      presetEncoding = presetEncoding.toUpperCase();
      int presetIdx  = -1;
      int nItems     = this.comboEncoding.getItemCount();
      for( int i = 0; i < nItems; i++ ) {
	Object item = this.comboEncoding.getItemAt( i );
	if( item != null ) {
	  if( item == this.nativeEncodingName ) {
	    if( this.nativeEncodingName != null ) {
	      if( this.nativeEncodingName.equalsIgnoreCase(
						presetEncoding ) )
	      {
		presetIdx = i;
		break;
	      }
	    }
	  } else if( item instanceof CharConverter ) {
	    if( ((CharConverter) item).equalsEncodingName(
						presetEncoding ) )
	    {
	      presetIdx = i;
	      break;
	    }
	  } else {
	    String s = item.toString();
	    if( s != null ) {
	      if( s.startsWith( presetEncoding ) ) {
		presetIdx = i;
		break;
	      }
	    }
	  }
	}
      }
      if( (presetIdx >= 0) && (presetIdx < nItems) ) {
	try {
	  this.comboEncoding.setSelectedIndex( presetIdx );
	}
	catch( IllegalArgumentException ex ) {}
      }
    }


    // Hinweistext
    JLabel label = GUIFactory.createLabel( "Achtung!" );
    Font font = label.getFont();
    if( font != null ) {
      label.setFont( font.deriveFont( Font.BOLD ) );
    }
    gbc.anchor        = GridBagConstraints.WEST;
    gbc.insets.top    = 20;
    gbc.insets.bottom = 0;
    gbc.gridy++;
    add( label, gbc );

    gbc.insets.top = 0;
    gbc.gridy++;
    add(
	GUIFactory.createLabel(
		"Die Datei wird als Textdatei mit dem"
			+ " ausgew\u00E4hlten Zeichensatz ge\u00F6ffnet." ),
	gbc );

    gbc.gridy++;
    add(
	GUIFactory.createLabel(
		"Das gilt auch, wenn die Datei gar keine"
			+ " Textdatei ist oder in einem" ),
	gbc );

    gbc.gridy++;
    add(
	GUIFactory.createLabel( "anderem Zeichensatz gespeichert wurde." ),
	gbc );

    this.cbIgnoreEofByte = GUIFactory.createCheckBox(
		"Eventuell vorhandenes Dateiendezeichen ignorieren" );
    gbc.anchor     = GridBagConstraints.CENTER;
    gbc.insets.top = 20;
    gbc.gridy++;
    add( this.cbIgnoreEofByte, gbc );


    // Knoepfe
    JPanel panelBtn = GUIFactory.createPanel(
				new GridLayout( 1, 2, 5, 5 ) ) ;

    this.btnOK = GUIFactory.createButtonOK();
    panelBtn.add( this.btnOK );

    this.btnCancel = GUIFactory.createButtonCancel();
    panelBtn.add( this.btnCancel );

    gbc.insets.top    = 10;
    gbc.insets.bottom = 10;
    gbc.gridy++;
    add( panelBtn, gbc );


    // Fenstergroesse und -position
    pack();
    setParentCentered();
    setResizable( false );
  }


  public boolean encodingChoosen()
  {
    return this.applied;
  }


  public CharConverter getCharConverter()
  {
    return this.charConverter;
  }


  public String getEncodingName()
  {
    return this.encodingName;
  }


  public String getEncodingDisplayText()
  {
    return this.encodingDisplayText;
  }


  public boolean getIgnoreEofByte()
  {
    return this.ignoreEofByte;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    if( !this.notified ) {
      this.notified = true;
      this.btnOK.addActionListener( this );
      this.btnCancel.addActionListener( this );
    }
  }


  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = false;
    if( e != null ) {
      Object src = e.getSource();
      if( src != null ) {
	if( src == this.btnOK ) {
	  doApply();
	}
	else if( src == this.btnCancel ) {
	  doClose();
	}
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
      this.btnOK.removeActionListener( this );
      this.btnCancel.removeActionListener( this );
    }
  }


	/* --- private Methoden --- */

  private void doApply()
  {
    Object encodingObj = this.comboEncoding.getSelectedItem();
    if( encodingObj != null ) {
      this.encodingDisplayText = encodingObj.toString();
      if( encodingObj instanceof CharConverter ) {
	this.charConverter = (CharConverter) encodingObj;
	this.encodingName  = charConverter.getEncodingName();
      } else {
	String s = encodingObj.toString();
	if( s != null ) {
	  if( TextUtil.equals( s, this.nativeEncodingItem ) ) {
	    this.encodingName = this.nativeEncodingName;
	  } else {
	    int pSpace = s.indexOf( '\u0020' );
	    this.encodingName = (pSpace > 0 ? s.substring( 0, pSpace ) : s);
	  }
	}
      }
    }
    this.ignoreEofByte = this.cbIgnoreEofByte.isSelected();
    this.applied       = true;
    doClose();
  }
}

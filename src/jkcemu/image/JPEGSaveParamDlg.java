/*
 * (c) 2022 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog zum Einstellen der Parameter beim Speichern von JPEG-Dateien
 */

package jkcemu.image;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.EventObject;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;


public class JPEGSaveParamDlg extends BaseDlg
{
  private static final int QUALITY_MIN = 1;
  private static final int QUALITY_MAX = 100;

  private JRadioButton rbQualityDefault;
  private JRadioButton rbQualityExplicit;
  private JSpinner     spinnerQuality;
  private JCheckBox    cbProgressiveMode;
  private JCheckBox    cbOptimHuffmTables;
  private JLabel       labelQualityUnit;
  private JButton      btnApply;
  private JButton      btnCancel;


  public static void showDlg( ImageFrm imageFrm )
  {
    (new JPEGSaveParamDlg( imageFrm )).setVisible( true );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( (src == this.rbQualityDefault)
	|| (src == this.rbQualityExplicit) )
    {
      rv = true;
      updFieldsEnabled();
    }
    else if( (src == this.btnApply)
	     || (src == this.spinnerQuality) )
    {
      rv = true;
      doApply();
    }
    else if( src == this.btnCancel ) {
      rv = true;
      doClose();
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = super.doClose();
    if( rv ) {
      this.rbQualityDefault.removeActionListener( this );
      this.rbQualityExplicit.removeActionListener( this );
      this.btnApply.removeActionListener( this );
      this.btnCancel.removeActionListener( this );
    }
    return rv;
  }


  @Override
  public void windowOpened( WindowEvent e )
  {
    if( e.getComponent() == this ) {
      if( this.rbQualityDefault.isSelected() ) {
	this.rbQualityDefault.requestFocus();
      } else if( this.rbQualityExplicit.isSelected() ) {
	this.spinnerQuality.requestFocus();
      }
    }
  }


	/* --- Konstruktor --- */

  private JPEGSaveParamDlg( Window owner )
  {
    super( owner, "JPEG-Parameter beim Speichern" );


    // Fensterinhalt
    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    add( GUIFactory.createLabel( "Qualit\u00E4t:" ), gbc );

    ButtonGroup grpQuality = new ButtonGroup();

    this.rbQualityDefault = GUIFactory.createRadioButton( "Standard" );
    grpQuality.add( this.rbQualityDefault );
    gbc.insets.top  = 0;
    gbc.insets.left = 50;
    gbc.gridy++;
    add( this.rbQualityDefault, gbc );

    this.rbQualityExplicit = GUIFactory.createRadioButton( "Festlegen auf:" );
    grpQuality.add( this.rbQualityExplicit );
    gbc.gridwidth = 1;
    gbc.gridy++;
    add( this.rbQualityExplicit, gbc );

    this.spinnerQuality = GUIFactory.createSpinner(
				new SpinnerNumberModel(
						85,
						QUALITY_MIN,
						QUALITY_MAX,
						1 ) );
    gbc.insets.left = 0;
    gbc.gridx++;
    add( this.spinnerQuality, gbc );

    this.labelQualityUnit = GUIFactory.createLabel( "%" );
    gbc.gridx++;
    add( this.labelQualityUnit, gbc );

    this.cbProgressiveMode = GUIFactory.createCheckBox(
			"Progressiver Modus forcieren",
			Main.getBooleanProperty(
				ImageSaver.PROP_JPEG_PROGRESSIVE_MODE,
				false ) );
    gbc.insets.left = 5;
    gbc.gridwidth   = GridBagConstraints.REMAINDER;
    gbc.gridx       = 0;
    gbc.gridy++;
    add( this.cbProgressiveMode, gbc );

    this.cbOptimHuffmTables = GUIFactory.createCheckBox(
			"Optimierte Huffman-Tabellen erzeugen",
			Main.getBooleanProperty(
				ImageSaver.PROP_JPEG_OPTIMIZE_HUFFMAN_TABLES,
				false ) );
    gbc.gridy++;
    add( this.cbOptimHuffmTables, gbc );


    // sonstige Schaltflaechen
    JPanel panelBtn = GUIFactory.createPanel(
				new GridLayout( 1, 2, 5, 5 ) );
    gbc.anchor        = GridBagConstraints.CENTER;
    gbc.insets.top    = 10;
    gbc.insets.bottom = 5;
    gbc.gridy++;
    add( panelBtn, gbc );

    this.btnApply = GUIFactory.createButtonOK();
    panelBtn.add( this.btnApply );

    this.btnCancel = GUIFactory.createButtonCancel();
    panelBtn.add( this.btnCancel );


    // Fenstergroesse und -position
    pack();
    setParentCentered();
    setResizable( false );


    // Vorbelegungen
    boolean done    = false;
    Integer quality = Main.getIntegerProperty(
				ImageSaver.PROP_JPEG_QUALITY_VALUE );
    if( quality != null ) {
      if( (quality.intValue() < 1) || (quality.intValue() > 100) ) {
	quality = null;
      }
    }
    if( quality != null ) {
      try {
	this.spinnerQuality.setValue( quality );
	if( Main.getBooleanProperty(
			ImageSaver.PROP_JPEG_QUALITY_EXPLICIT,
			false ) )
	{
	  this.rbQualityExplicit.setSelected( true );
	  done = true;
	}
      }
      catch( IllegalArgumentException ex ) {}
    }
    if( !done ) {
      this.rbQualityDefault.setSelected( true );
    }
    updFieldsEnabled();


    // Listener
    this.rbQualityDefault.addActionListener( this );
    this.rbQualityExplicit.addActionListener( this );
    this.btnApply.addActionListener( this );
    this.btnCancel.addActionListener( this );
  }


	/* --- private Methoden --- */

  private void doApply()
  {
    try {
      this.spinnerQuality.commitEdit();
      boolean state = false;
      String  text = EmuUtil.TEXT_DEFAULT;
      Object  obj  = this.spinnerQuality.getValue();
      if( obj != null ) {
	text  = obj.toString();
	state = true;
      }
      state &= this.rbQualityExplicit.isSelected();
      Main.setProperty( ImageSaver.PROP_JPEG_QUALITY_VALUE, text );
      Main.setProperty(
		ImageSaver.PROP_JPEG_QUALITY_EXPLICIT,
		String.valueOf( state ) );
      Main.setProperty(
		ImageSaver.PROP_JPEG_PROGRESSIVE_MODE,
		String.valueOf( this.cbProgressiveMode.isSelected() ) );
      Main.setProperty(
		ImageSaver.PROP_JPEG_OPTIMIZE_HUFFMAN_TABLES,
		String.valueOf( this.cbOptimHuffmTables.isSelected() ) );
      doClose();
    }
    catch( ParseException ex ) {
      showErrorDlg( this, "Ung\u00FCltige Eingabe" );
    }
  }


  private void moveQualitySlider( int diffValue )
  {
    try {
      Object obj = this.spinnerQuality.getValue();
      if( obj != null ) {
	if( obj instanceof Number ) {
	  int value = ((Number) obj).intValue() + diffValue;
	  value     = Math.max( value, QUALITY_MIN );
	  value     = Math.min( value, QUALITY_MAX );
	  this.spinnerQuality.setValue( value );
	}
      }
    }
    catch( IllegalArgumentException ex ) {}
  }


  private void updFieldsEnabled()
  {
    boolean state = this.rbQualityExplicit.isSelected();
    this.spinnerQuality.setEnabled( state );
    this.labelQualityUnit.setEnabled( state );
  }
}

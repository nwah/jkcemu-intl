/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog fuer Assembler-Optionen
 */

package jkcemu.programming.assembler;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EventObject;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.UserInputException;
import jkcemu.lang.LangUtil;
import jkcemu.programming.AbstractOptionsDlg;
import jkcemu.programming.PrgOptions;


public class AsmOptionsDlg extends AbstractOptionsDlg
{
  private static final int DEFAULT_LIST_PAGE_LEN = 53;

  private boolean      notified;
  private JTabbedPane  tabbedPane;
  private JRadioButton rbSyntaxZilog;
  private JRadioButton rbSyntaxRobotron;
  private JRadioButton rbSyntaxBoth;
  private JCheckBox    cbAllowUndocInst;
  private JCheckBox    cbAsmListing;
  private JCheckBox    cbLabelsCaseSensitive;
  private JCheckBox    cbPrintLabels;
  private JCheckBox    cbLabelsToReass;
  private JCheckBox    cbLabelsToDebugger;
  private JCheckBox    cbFormatSource;
  private JCheckBox    cbReplaceTooLongRelJumps;
  private JCheckBox    cbWarnNonAsciiChars;
  private JLabel       labelAsmListPageLen;
  private JSpinner     spinnerAsmListPageLen;
  private JRadioButton rbLabelsCreateOrUpdateBPs;
  private JRadioButton rbLabelsUpdateBPsOnly;


  public AsmOptionsDlg(
		Frame      owner,
		EmuThread  emuThread,
		PrgOptions options )
  {
    super( owner, emuThread, options, "Assembler-Optionen" );
    this.notified = false;


    // Fensterinhalt
    setLayout( new GridBagLayout() );
    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					1.0, 1.0,
					GridBagConstraints.CENTER,
					GridBagConstraints.BOTH,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    this.tabbedPane = GUIFactory.createTabbedPane();
    add( this.tabbedPane, gbc );


    // Bereich Mnemonik/Syntax
    JPanel panelSyntax = GUIFactory.createPanel( new GridBagLayout() );
    this.tabbedPane.addTab( "Mnemonik/Syntax", panelSyntax );

    GridBagConstraints gbcSyntax = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    ButtonGroup grpSyntax = new ButtonGroup();

    this.rbSyntaxBoth = GUIFactory.createRadioButton(
				"Zilog- und Robotron-Mnemonik/-Syntax" );
    grpSyntax.add( this.rbSyntaxBoth );
    panelSyntax.add( this.rbSyntaxBoth, gbcSyntax );

    this.rbSyntaxZilog = GUIFactory.createRadioButton(
				"Nur Zilog-Mnemonik/-Syntax erlauben" );
    grpSyntax.add( this.rbSyntaxZilog );
    gbcSyntax.insets.top = 0;
    gbcSyntax.gridy++;
    panelSyntax.add( this.rbSyntaxZilog, gbcSyntax );

    this.rbSyntaxRobotron = GUIFactory.createRadioButton(
				"Nur Robotron-Mnemonik/-Syntax erlauben" );
    grpSyntax.add( this.rbSyntaxRobotron );
    gbcSyntax.insets.bottom = 5;
    gbcSyntax.gridy++;
    panelSyntax.add( this.rbSyntaxRobotron, gbcSyntax );

    this.cbAllowUndocInst = GUIFactory.createCheckBox(
				"Undokumentierte Befehle erlauben" );
    gbcSyntax.insets.top = 0;
    gbcSyntax.gridy++;
    panelSyntax.add( this.cbAllowUndocInst, gbcSyntax );


    // Bereich Marken
    JPanel panelLabel = GUIFactory.createPanel( new GridBagLayout( ));
    this.tabbedPane.addTab( "Marken", panelLabel );

    GridBagConstraints gbcLabel = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    this.cbLabelsCaseSensitive = GUIFactory.createCheckBox(
			"Gro\u00DF-/Kleinschreibung bei Marken beachten" );
    panelLabel.add( this.cbLabelsCaseSensitive, gbcLabel );

    this.cbPrintLabels = GUIFactory.createCheckBox(
					"Markentabelle ausgeben" );
    gbcLabel.insets.top = 0;
    gbcLabel.gridy++;
    panelLabel.add( this.cbPrintLabels, gbcLabel );

    this.cbLabelsToDebugger = GUIFactory.createCheckBox(
					"Marken im Debugger verwenden" );
    this.cbLabelsToDebugger.setEnabled( false );
    gbcLabel.gridy++;
    panelLabel.add( this.cbLabelsToDebugger, gbcLabel );

    ButtonGroup grpLabelInDebugger = new ButtonGroup();

    this.rbLabelsCreateOrUpdateBPs = GUIFactory.createRadioButton(
		"Halte-/Log-Punkte und Variablen auf Marken anlegen"
				+ " bzw. aktualisieren" );
    grpLabelInDebugger.add( this.rbLabelsCreateOrUpdateBPs );
    gbcLabel.insets.left = 50;
    gbcLabel.gridy++;
    panelLabel.add( this.rbLabelsCreateOrUpdateBPs, gbcLabel );

    this.rbLabelsUpdateBPsOnly = GUIFactory.createRadioButton(
		"Nur vorhandene benamte Halte-/Log-Punkte und Variablen"
				+ " aktualisieren" );
    grpLabelInDebugger.add( this.rbLabelsUpdateBPsOnly );
    gbcLabel.gridy++;
    panelLabel.add( this.rbLabelsUpdateBPsOnly, gbcLabel );

    this.cbLabelsToReass = GUIFactory.createCheckBox(
				"Marken im Reassembler verwenden" );
    this.cbLabelsToReass.setEnabled( false );
    gbcLabel.insets.left   = 5;
    gbcLabel.insets.bottom = 5;
    gbcLabel.gridy++;
    panelLabel.add( this.cbLabelsToReass, gbcLabel );


    // Bereich Erzeugter Programmcode
    JPanel panelCodeDest = createCodeDestOptions( true );
    this.tabbedPane.addTab( "Erzeugter Programmcode", panelCodeDest );


    // Bereich Sonstiges
    JPanel panelEtc = GUIFactory.createPanel( new GridBagLayout() );
    this.tabbedPane.addTab( "Sonstiges", panelEtc );

    GridBagConstraints gbcEtc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );


    this.cbWarnNonAsciiChars = GUIFactory.createCheckBox(
					"Bei Nicht-ASCII-Zeichen warnen" );
    panelEtc.add( this.cbWarnNonAsciiChars, gbcEtc );

    this.cbReplaceTooLongRelJumps = GUIFactory.createCheckBox(
		"Zu gro\u00DFe relative Spr\u00FCnge als absolute"
			+ " \u00FCbersetzen (nicht bei DJNZ)" );
    gbcEtc.insets.top = 0;
    gbcEtc.gridy++;
    panelEtc.add( this.cbReplaceTooLongRelJumps, gbcEtc );

    this.cbFormatSource = GUIFactory.createCheckBox(
					"Quelltext formatieren" );
    gbcEtc.gridy++;
    panelEtc.add( this.cbFormatSource, gbcEtc );

    this.cbAsmListing = GUIFactory.createCheckBox(
					"Assembler-Listing erzeugen" );
    gbcEtc.gridy++;
    panelEtc.add( this.cbAsmListing, gbcEtc );

    this.labelAsmListPageLen = GUIFactory.createLabel(
	"Zeilen pro Seite (0: keine automatischen Seitenumbr\u00FCche):" );
    gbcEtc.insets.left   = 50;
    gbcEtc.insets.bottom = 5;
    gbcEtc.gridwidth     = 1;
    gbcEtc.gridy++;
    panelEtc.add( this.labelAsmListPageLen, gbcEtc );

    this.spinnerAsmListPageLen = GUIFactory.createSpinner(
		new SpinnerNumberModel( DEFAULT_LIST_PAGE_LEN, 0, 999, 1 ) );
    gbcEtc.insets.left = 5;
    gbcEtc.gridx++;
    panelEtc.add( this.spinnerAsmListPageLen, gbcEtc );


    // Bereich Knoepfe
    gbc.fill          = GridBagConstraints.NONE;
    gbc.weightx       = 0.0;
    gbc.weighty       = 0.0;
    gbc.insets.bottom = 10;
    gbc.gridy++;
    add( createButtons( "Assemblieren" ), gbc );


    // Vorbelegungen
    if( options != null ) {
      switch( options.getAsmSyntax() ) {
	case ZILOG_ONLY:
	  this.rbSyntaxZilog.setSelected( true );
	  break;
	case ROBOTRON_ONLY:
	  this.rbSyntaxRobotron.setSelected( true );
	  break;
	default:
	  this.rbSyntaxBoth.setSelected( true );
	  break;
      }
      this.cbAllowUndocInst.setSelected( options.getAllowUndocInst() );
      this.cbAsmListing.setSelected( options.getCreateAsmListing() );
      try {
	this.spinnerAsmListPageLen.setValue(
		Integer.valueOf( options.getAsmListPageLength() ) );
      }
      catch( IllegalArgumentException ex ) {}
      this.cbLabelsCaseSensitive.setSelected(
					options.getLabelsCaseSensitive() );
      this.cbPrintLabels.setSelected( options.getPrintLabels() );
      this.cbWarnNonAsciiChars.setSelected( options.getWarnNonAsciiChars() );
      this.cbReplaceTooLongRelJumps.setSelected(
				options.getReplaceTooLongRelJumps() );
      this.cbFormatSource.setSelected( options.getFormatSource() );
      this.cbLabelsToDebugger.setSelected( options.getLabelsToDebugger() );
      if( options.getLabelsUpdateBreakpointsOnly() ) {
	this.rbLabelsUpdateBPsOnly.setSelected( true );
      } else {
	this.rbLabelsCreateOrUpdateBPs.setSelected( true );
      }
      this.cbLabelsToReass.setSelected( options.getLabelsToReassembler() );
      updCodeDestFields( options, false );
    } else {
      this.rbSyntaxBoth.setSelected( true );
      this.cbAllowUndocInst.setSelected( false );
      this.cbAsmListing.setSelected( false );
      this.cbLabelsCaseSensitive.setSelected( false );
      this.cbPrintLabels.setSelected( false );
      this.cbWarnNonAsciiChars.setSelected( true );
      this.cbReplaceTooLongRelJumps.setSelected( false );
      this.cbFormatSource.setSelected( false );
      this.cbLabelsToDebugger.setSelected( true );
      this.rbLabelsUpdateBPsOnly.setSelected( true );
      this.cbLabelsToReass.setSelected( false );
      updCodeDestFields( options, true );
    }
    updAsmListingsActionsEnabled();
    updLabelToDebuggerActionsEnabled();


    // Fenstergroesse und -position
    pack();
    setParentCentered();
    setResizable( false );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    if( !this.notified ) {
      this.notified = true;
      if( this.cbLabelsToDebugger != null ) {
	this.cbLabelsToDebugger.addActionListener( this );
      }
      if( this.cbAsmListing != null ) {
	this.cbAsmListing.addActionListener( this );
      }
    }
  }


  @Override
  protected void codeToEmuChanged( boolean state )
  {
    this.cbLabelsToDebugger.setEnabled( state );
    this.cbLabelsToReass.setEnabled( state );
    updLabelToDebuggerActionsEnabled();
  }


  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = super.doAction( e );
    if( !rv ) {
      Object src = e.getSource();
      if( src == this.cbLabelsToDebugger ) {
	updLabelToDebuggerActionsEnabled();
      }
      else if( src == this.cbAsmListing ) {
	updAsmListingsActionsEnabled();
      }
      rv = true;
    }
    return rv;
  }


  @Override
  protected void doApply()
  {
    try {
      Z80Assembler.Syntax syntax = Z80Assembler.Syntax.ALL;
      if( this.rbSyntaxZilog.isSelected() ) {
	syntax = Z80Assembler.Syntax.ZILOG_ONLY;
      }
      else if( this.rbSyntaxRobotron.isSelected() ) {
	syntax = Z80Assembler.Syntax.ROBOTRON_ONLY;
      }
      this.appliedOptions = new PrgOptions( this.oldOptions );
      this.appliedOptions.setAsmSyntax( syntax );
      this.appliedOptions.setAllowUndocInst(
			this.cbAllowUndocInst.isSelected() );
      this.appliedOptions.setCreateAsmListing(
			this.cbAsmListing.isSelected() );
      this.appliedOptions.setAsmListPageLength(
			EmuUtil.getInt( this.spinnerAsmListPageLen ) );
      this.appliedOptions.setLabelsCaseSensitive(
			this.cbLabelsCaseSensitive.isSelected() );
      this.appliedOptions.setPrintLabels( this.cbPrintLabels.isSelected() );
      this.appliedOptions.setLabelsToDebugger(
			this.cbLabelsToDebugger.isSelected() );
      this.appliedOptions.setLabelsUpdateBreakpointsOnly(
			this.rbLabelsUpdateBPsOnly.isSelected() );
      this.appliedOptions.setLabelsToReassembler(
			this.cbLabelsToReass.isSelected() );
      this.appliedOptions.setFormatSource(
			this.cbFormatSource.isSelected() );
      this.appliedOptions.setReplaceTooLongRelJumps(
			this.cbReplaceTooLongRelJumps.isSelected() );
      this.appliedOptions.setWarnNonAsciiChars(
			this.cbWarnNonAsciiChars.isSelected() );
      try {
	applyCodeDestOptionsTo( this.appliedOptions );
	doClose();
      }
      catch( UserInputException ex ) {
	showErrorDlg(
		this,
		LangUtil.tr( "Erzeugter Programmcode:" )
			+ "\n" + ex.getMessage() );
      }
    }
    catch( NumberFormatException ex ) {
      showErrorDlg( this, ex.getMessage() );
    }
  }


  @Override
  public void removeNotify()
  {
    if( this.notified ) {
      this.notified = false;
      if( this.cbLabelsToDebugger != null ) {
	this.cbLabelsToDebugger.removeActionListener( this );
      }
      if( this.cbAsmListing != null ) {
	this.cbAsmListing.removeActionListener( this );
      }
    }
    super.removeNotify();
  }


	/* --- private Methoden --- */

  private void updAsmListingsActionsEnabled()
  {
    boolean state = this.cbAsmListing.isSelected();
    this.labelAsmListPageLen.setEnabled( state );
    this.spinnerAsmListPageLen.setEnabled( state );
  }


  private void updLabelToDebuggerActionsEnabled()
  {
    boolean state = this.cbLabelsToDebugger.isEnabled()
			&& this.cbLabelsToDebugger.isSelected();
    this.rbLabelsCreateOrUpdateBPs.setEnabled( state );
    this.rbLabelsUpdateBPsOnly.setEnabled( state );
  }
}

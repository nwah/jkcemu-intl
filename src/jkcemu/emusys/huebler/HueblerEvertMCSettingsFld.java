/*
 * (c) 2016-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponente fuer die Einstellungen des Huebler/Evert-MC
 */

package jkcemu.emusys.huebler;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EventObject;
import java.util.Properties;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import jkcemu.base.AutoInputCharSet;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.UserInputException;
import jkcemu.emusys.HueblerEvertMC;
import jkcemu.file.ROMFileSettingsFld;
import jkcemu.lang.LangUtil;
import jkcemu.settings.AbstractSettingsFld;
import jkcemu.settings.AutoInputSettingsFld;
import jkcemu.settings.AutoLoadSettingsFld;
import jkcemu.settings.SettingsFrm;


public class HueblerEvertMCSettingsFld extends AbstractSettingsFld
{
  private JTabbedPane          tabbedPane;
  private JPanel               tabEtc;
  private AutoLoadSettingsFld  tabAutoLoad;
  private AutoInputSettingsFld tabAutoInput;
  private JCheckBox            cbCatchPrintCalls;
  private ROMFileSettingsFld   fldAltOS;
  private ROMFileSettingsFld   fldAltFont;


  public HueblerEvertMCSettingsFld(
			SettingsFrm settingsFrm,
			String      propPrefix )
  {
    super( settingsFrm, propPrefix );

    setLayout( new BorderLayout() );

    this.tabbedPane = GUIFactory.createTabbedPane();
    add( this.tabbedPane, BorderLayout.CENTER );


    // Tab AutoLoad
    this.tabAutoLoad = new AutoLoadSettingsFld(
		settingsFrm,
		propPrefix,
		HueblerEvertMC.DEFAULT_PROMPT_AFTER_RESET_MILLIS_MAX,
		true );
    this.tabbedPane.addTab( LangUtil.getText( "emusys.section.autoload" ),
		this.tabAutoLoad );


    // Tab AutoInput
    this.tabAutoInput = new AutoInputSettingsFld(
		settingsFrm,
		propPrefix,
		AutoInputCharSet.getStdCharSet(),
		HueblerEvertMC.DEFAULT_SWAP_KEY_CHAR_CASE,
		HueblerEvertMC.DEFAULT_PROMPT_AFTER_RESET_MILLIS_MAX );
    this.tabbedPane.addTab( LangUtil.getText( "emusys.section.autoinput" ),
		this.tabAutoInput );


    // Tab Sonstiges
    this.tabEtc = GUIFactory.createPanel( new GridBagLayout() );
    this.tabbedPane.addTab( LangUtil.getText( "common.section.miscellaneous" ),
		this.tabEtc );

    GridBagConstraints gbcEtc = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    this.cbCatchPrintCalls = GUIFactory.createCheckBox(
		LangUtil.getText( "emusys.option.intercept_operating" ),
		true );

    this.tabEtc.add( this.cbCatchPrintCalls, gbcEtc );

    gbcEtc.fill          = GridBagConstraints.HORIZONTAL;
    gbcEtc.weightx       = 1.0;
    gbcEtc.insets.top    = 10;
    gbcEtc.insets.bottom = 10;
    gbcEtc.gridy++;
    this.tabEtc.add( GUIFactory.createSeparator(), gbcEtc );

    this.fldAltOS = new ROMFileSettingsFld(
		settingsFrm,
		propPrefix + HueblerEvertMC.PROP_OS_PREFIX,
		LangUtil.getText( "huebler.text.alternative_monitor" ) );
    gbcEtc.insets.top    = 5;
    gbcEtc.insets.bottom = 5;
    gbcEtc.gridy++;
    this.tabEtc.add( this.fldAltOS, gbcEtc );

    this.fldAltFont = new ROMFileSettingsFld(
				settingsFrm,
				propPrefix + HueblerEvertMC.PROP_FONT_PREFIX,
				LangUtil.getText( "emusys.text.alternative_character" ) );
    gbcEtc.gridy++;
    this.tabEtc.add( this.fldAltFont, gbcEtc );


    // Listener
    this.cbCatchPrintCalls.addActionListener( this );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void applyInput(
		Properties props,
		boolean    selected ) throws UserInputException
  {
    EmuUtil.setProperty(
		props,
		this.propPrefix + HueblerEvertMC.PROP_CATCH_PRINT_CALLS,
		this.cbCatchPrintCalls.isSelected() );

    this.fldAltOS.applyInput( props, selected );
    this.fldAltFont.applyInput( props, selected );

    this.tabAutoLoad.applyInput( props, selected );
    this.tabAutoInput.applyInput( props, selected );
  }


  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src != null ) {
      rv = this.tabAutoLoad.doAction( e );
      if( !rv ) {
	rv = this.tabAutoInput.doAction( e );
      }
      if( !rv && (src instanceof AbstractButton) ) {
        rv = true;
        fireDataChanged();
      }
    }
    return rv;
  }


  @Override
  public void updFields( Properties props )
  {
    this.cbCatchPrintCalls.setSelected(
	EmuUtil.getBooleanProperty(
		props,
		this.propPrefix + HueblerEvertMC.PROP_CATCH_PRINT_CALLS,
		true ) );

    this.fldAltOS.updFields( props );
    this.fldAltFont.updFields( props );

    this.tabAutoLoad.updFields( props );
    this.tabAutoInput.updFields( props );
  }
}

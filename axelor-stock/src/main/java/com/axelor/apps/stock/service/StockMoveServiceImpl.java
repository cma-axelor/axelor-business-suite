/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.stock.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.FreightCarrierMode;
import com.axelor.apps.stock.db.Incoterm;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.ShipmentMode;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.InventoryLineRepository;
import com.axelor.apps.stock.db.repo.InventoryRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.report.IReport;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockMoveServiceImpl implements StockMoveService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected StockMoveLineService stockMoveLineService;
  protected AppBaseService appBaseService;
  protected StockMoveRepository stockMoveRepo;
  protected PartnerProductQualityRatingService partnerProductQualityRatingService;
  private StockMoveToolService stockMoveToolService;
  private StockMoveLineRepository stockMoveLineRepo;

  @Inject
  public StockMoveServiceImpl(
      StockMoveLineService stockMoveLineService,
      StockMoveToolService stockMoveToolService,
      StockMoveLineRepository stockMoveLineRepository,
      AppBaseService appBaseService,
      StockMoveRepository stockMoveRepository,
      PartnerProductQualityRatingService partnerProductQualityRatingService) {
    this.stockMoveLineService = stockMoveLineService;
    this.stockMoveToolService = stockMoveToolService;
    this.stockMoveLineRepo = stockMoveLineRepository;
    this.appBaseService = appBaseService;
    this.stockMoveRepo = stockMoveRepository;
    this.partnerProductQualityRatingService = partnerProductQualityRatingService;
  }

  /**
   * Generic method to create any stock move
   *
   * @param fromAddress
   * @param toAddress
   * @param company
   * @param clientPartner
   * @param fromStockLocation
   * @param toStockLocation
   * @param realDate
   * @param estimatedDate
   * @param description
   * @param shipmentMode
   * @param freightCarrierMode
   * @param carrierPartner
   * @param forwarderPartner
   * @param incoterm
   * @return
   * @throws AxelorException No Stock move sequence defined
   */
  @Override
  public StockMove createStockMove(
      Address fromAddress,
      Address toAddress,
      Company company,
      Partner clientPartner,
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      LocalDate realDate,
      LocalDate estimatedDate,
      String description,
      ShipmentMode shipmentMode,
      FreightCarrierMode freightCarrierMode,
      Partner carrierPartner,
      Partner forwarderPartner,
      Incoterm incoterm,
      int typeSelect)
      throws AxelorException {

    StockMove stockMove =
        this.createStockMove(
            fromAddress,
            toAddress,
            company,
            fromStockLocation,
            toStockLocation,
            realDate,
            estimatedDate,
            description,
            typeSelect);
    stockMove.setPartner(clientPartner);
    stockMove.setShipmentMode(shipmentMode);
    stockMove.setFreightCarrierMode(freightCarrierMode);
    stockMove.setCarrierPartner(carrierPartner);
    stockMove.setForwarderPartner(forwarderPartner);
    stockMove.setIncoterm(incoterm);
    stockMove.setIsIspmRequired(stockMoveToolService.getDefaultISPM(clientPartner, toAddress));

    return stockMove;
  }

  /**
   * Generic method to create any stock move for internal stock move (without partner information)
   *
   * @param fromAddress
   * @param toAddress
   * @param company
   * @param fromStockLocation
   * @param toStockLocation
   * @param realDate
   * @param estimatedDate
   * @param description
   * @param typeSelect
   * @return
   * @throws AxelorException No Stock move sequence defined
   */
  @Override
  public StockMove createStockMove(
      Address fromAddress,
      Address toAddress,
      Company company,
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      LocalDate realDate,
      LocalDate estimatedDate,
      String description,
      int typeSelect)
      throws AxelorException {

    StockMove stockMove = new StockMove();

    if (stockMove.getStockMoveLineList() == null) {
      stockMove.setStockMoveLineList(new ArrayList<>());
    }

    stockMove.setFromAddress(fromAddress);
    stockMove.setToAddress(toAddress);
    stockMoveToolService.computeAddressStr(stockMove);
    stockMove.setCompany(company);
    stockMove.setStatusSelect(StockMoveRepository.STATUS_DRAFT);
    stockMove.setRealDate(realDate);
    stockMove.setEstimatedDate(estimatedDate);
    stockMove.setFromStockLocation(fromStockLocation);
    stockMove.setToStockLocation(toStockLocation);
    stockMove.setDescription(description);

    stockMove.setPrintingSettings(
        Beans.get(TradingNameService.class).getDefaultPrintingSettings(null, company));

    stockMove.setTypeSelect(typeSelect);

    return stockMove;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void validate(StockMove stockMove) throws AxelorException {

    this.plan(stockMove);
    this.realize(stockMove);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void goBackToDraft(StockMove stockMove) throws AxelorException {
    if (stockMove.getStatusSelect() != StockMoveRepository.STATUS_CANCELED) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.STOCK_MOVE_CANNOT_GO_BACK_TO_DRAFT));
    }

    stockMove.setStatusSelect(StockMoveRepository.STATUS_DRAFT);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void plan(StockMove stockMove) throws AxelorException {

    LOG.debug("Planification du mouvement de stock : {} ", stockMove.getStockMoveSeq());

    if (stockMove.getExTaxTotal().compareTo(BigDecimal.ZERO) == 0) {
      stockMove.setExTaxTotal(stockMoveToolService.compute(stockMove));
    }

    StockLocation fromStockLocation = stockMove.getFromStockLocation();
    StockLocation toStockLocation = stockMove.getToStockLocation();

    if (fromStockLocation == null) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_5),
          stockMove.getName());
    }
    if (toStockLocation == null) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_6),
          stockMove.getName());
    }

    // Set the type select
    if (stockMove.getTypeSelect() == null || stockMove.getTypeSelect() == 0) {
      stockMove.setTypeSelect(
          stockMoveToolService.getStockMoveType(fromStockLocation, toStockLocation));
    }

    String draftSeq;

    // Set the sequence.
    if (Beans.get(SequenceService.class)
        .isEmptyOrDraftSequenceNumber(stockMove.getStockMoveSeq())) {
      draftSeq = stockMove.getStockMoveSeq();
      stockMove.setStockMoveSeq(
          stockMoveToolService.getSequenceStockMove(
              stockMove.getTypeSelect(), stockMove.getCompany()));
    } else {
      draftSeq = null;
    }

    if (Strings.isNullOrEmpty(stockMove.getName())
        || draftSeq != null && stockMove.getName().startsWith(draftSeq)) {
      stockMove.setName(stockMoveToolService.computeName(stockMove));
    }

    if (stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(appBaseService.getTodayDate());
    }

    updateLocations(stockMove, fromStockLocation, toStockLocation);

    stockMove.setStatusSelect(StockMoveRepository.STATUS_PLANNED);

    stockMove.setCancelReason(null);

    stockMoveRepo.save(stockMove);
    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING
        && stockMove.getPlannedStockMoveAutomaticMail()) {
      sendMailForStockMove(stockMove, stockMove.getPlannedStockMoveMessageTemplate());
    }
  }

  /**
   * Update locations from a planned stock move, by copying stock move lines in the stock move then
   * updating locations.
   *
   * @param stockMove
   * @param fromStockLocation
   * @param toStockLocation
   * @throws AxelorException
   */
  protected void updateLocations(
      StockMove stockMove, StockLocation fromStockLocation, StockLocation toStockLocation)
      throws AxelorException {

    copyPlannedStockMovLines(stockMove);
    stockMoveLineService.updateLocations(
        fromStockLocation,
        toStockLocation,
        stockMove.getStatusSelect(),
        StockMoveRepository.STATUS_PLANNED,
        stockMove.getPlannedStockMoveLineList(),
        stockMove.getEstimatedDate(),
        false);
  }

  protected void copyPlannedStockMovLines(StockMove stockMove) {
    List<StockMoveLine> stockMoveLineList =
        MoreObjects.firstNonNull(stockMove.getStockMoveLineList(), Collections.emptyList());
    stockMove.clearPlannedStockMoveLineList();

    stockMoveLineList.forEach(
        stockMoveLine -> {
          StockMoveLine copy = stockMoveLineRepo.copy(stockMoveLine, false);
          copy.setArchived(true);
          stockMove.addPlannedStockMoveLineListItem(copy);
        });
  }

  @Override
  public String realize(StockMove stockMove) throws AxelorException {
    return realize(stockMove, true);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public String realize(StockMove stockMove, boolean checkOngoingInventoryFlag)
      throws AxelorException {
    LOG.debug(
        "Réalisation du mouvement de stock : {} ", new Object[] {stockMove.getStockMoveSeq()});

    if (checkOngoingInventoryFlag) {
      checkOngoingInventory(stockMove);
    }

    String newStockSeq = null;
    stockMoveLineService.checkTrackingNumber(stockMove);
    stockMoveLineService.checkConformitySelection(stockMove);
    checkExpirationDates(stockMove);

    stockMoveLineService.updateLocations(
        stockMove.getFromStockLocation(),
        stockMove.getToStockLocation(),
        stockMove.getStatusSelect(),
        StockMoveRepository.STATUS_CANCELED,
        stockMove.getPlannedStockMoveLineList(),
        stockMove.getEstimatedDate(),
        false);

    stockMoveLineService.updateLocations(
        stockMove.getFromStockLocation(),
        stockMove.getToStockLocation(),
        StockMoveRepository.STATUS_DRAFT,
        StockMoveRepository.STATUS_REALIZED,
        stockMove.getStockMoveLineList(),
        stockMove.getEstimatedDate(),
        true);

    stockMove.clearPlannedStockMoveLineList();

    stockMoveLineService.storeCustomsCodes(stockMove.getStockMoveLineList());

    stockMove.setStatusSelect(StockMoveRepository.STATUS_REALIZED);
    stockMove.setRealDate(appBaseService.getTodayDate());
    resetMasses(stockMove);

    try {
      if (stockMove.getIsWithBackorder() && mustBeSplit(stockMove.getStockMoveLineList())) {
        Optional<StockMove> newStockMove = copyAndSplitStockMove(stockMove);
        if (newStockMove.isPresent()) {
          newStockSeq = newStockMove.get().getStockMoveSeq();
        }
      }

      if (stockMove.getIsWithReturnSurplus() && mustBeSplit(stockMove.getStockMoveLineList())) {
        Optional<StockMove> newStockMove = copyAndSplitStockMoveReverse(stockMove, true);
        if (newStockMove.isPresent()) {
          if (newStockSeq != null) {
            newStockSeq = newStockSeq + " " + newStockMove.get().getStockMoveSeq();
          } else {
            newStockSeq = newStockMove.get().getStockMoveSeq();
          }
        }
      }
    } finally {
      computeMasses(stockMove);
      stockMoveRepo.save(stockMove);
    }

    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING) {
      partnerProductQualityRatingService.calculate(stockMove);
    } else if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING
        && stockMove.getRealStockMoveAutomaticMail()) {
      sendMailForStockMove(stockMove, stockMove.getRealStockMoveMessageTemplate());
    }

    return newStockSeq;
  }

  /**
   * Generate and send mail. Throws exception if the template is not found or if there is an error
   * while generating the message.
   */
  protected void sendMailForStockMove(StockMove stockMove, Template template)
      throws AxelorException {
    if (template == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_MISSING_TEMPLATE),
          stockMove);
    }
    try {
      Beans.get(TemplateMessageService.class).generateAndSendMessage(stockMove, template);
    } catch (Exception e) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, e.getMessage(), stockMove);
    }
  }

  /**
   * Check and raise an exception if the provided stock move is involved in an ongoing inventory.
   *
   * @param stockMove
   * @throws AxelorException
   */
  private void checkOngoingInventory(StockMove stockMove) throws AxelorException {
    List<StockLocation> stockLocationList = new ArrayList<>();

    if (stockMove.getFromStockLocation().getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      stockLocationList.add(stockMove.getFromStockLocation());
    }

    if (stockMove.getToStockLocation().getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      stockLocationList.add(stockMove.getToStockLocation());
    }

    if (stockLocationList.isEmpty()) {
      return;
    }

    List<Product> productList =
        stockMove
            .getStockMoveLineList()
            .stream()
            .map(StockMoveLine::getProduct)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (productList.isEmpty()) {
      return;
    }

    InventoryLineRepository inventoryLineRepo = Beans.get(InventoryLineRepository.class);

    InventoryLine inventoryLine =
        inventoryLineRepo
            .all()
            .filter(
                "self.inventory.statusSelect BETWEEN :startStatus AND :endStatus\n"
                    + "AND self.inventory.stockLocation IN (:stockLocationList)\n"
                    + "AND self.product IN (:productList)")
            .bind("startStatus", InventoryRepository.STATUS_IN_PROGRESS)
            .bind("endStatus", InventoryRepository.STATUS_COMPLETED)
            .bind("stockLocationList", stockLocationList)
            .bind("productList", productList)
            .fetchOne();

    if (inventoryLine != null) {
      throw new AxelorException(
          inventoryLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.STOCK_MOVE_19),
          inventoryLine.getInventory().getInventorySeq());
    }
  }

  private void resetMasses(StockMove stockMove) {
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

    if (stockMoveLineList == null) {
      return;
    }

    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      stockMoveLine.setTotalNetMass(null);
    }
  }

  private void computeMasses(StockMove stockMove) throws AxelorException {
    StockConfig stockConfig = stockMove.getCompany().getStockConfig();
    Unit endUnit = stockConfig != null ? stockConfig.getCustomsMassUnit() : null;
    boolean massesRequiredForStockMove = false;

    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

    if (stockMoveLineList == null) {
      return;
    }

    UnitConversionService unitConversionService = Beans.get(UnitConversionService.class);

    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      Product product = stockMoveLine.getProduct();
      boolean massesRequiredForStockMoveLine =
          stockMoveLineService.checkMassesRequired(stockMove, stockMoveLine);

      if (product == null
          || !ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect())) {
        continue;
      }

      BigDecimal netMass = stockMoveLine.getNetMass();

      if (netMass.signum() == 0) {
        Unit startUnit = product.getMassUnit();

        if (startUnit != null && endUnit != null) {
          netMass =
              unitConversionService.convert(
                  startUnit, endUnit, product.getNetMass(), product.getNetMass().scale(), null);
          stockMoveLine.setNetMass(netMass);
        }
      }

      if (netMass.signum() != 0) {
        BigDecimal totalNetMass = netMass.multiply(stockMoveLine.getRealQty());
        stockMoveLine.setTotalNetMass(totalNetMass);
      } else if (massesRequiredForStockMoveLine) {
        throw new AxelorException(
            stockMove,
            TraceBackRepository.CATEGORY_NO_VALUE,
            I18n.get(IExceptionMessage.STOCK_MOVE_18));
      }

      if (!massesRequiredForStockMove && massesRequiredForStockMoveLine) {
        massesRequiredForStockMove = true;
      }
    }

    if (massesRequiredForStockMove && endUnit == null) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.STOCK_MOVE_17));
    }
  }

  @Override
  public boolean mustBeSplit(List<StockMoveLine> stockMoveLineList) {

    for (StockMoveLine stockMoveLine : stockMoveLineList) {

      if (stockMoveLine.getRealQty().compareTo(stockMoveLine.getQty()) != 0) {

        return true;
      }
    }

    return false;
  }

  @Override
  public Optional<StockMove> copyAndSplitStockMove(StockMove stockMove) throws AxelorException {
    return copyAndSplitStockMove(stockMove, stockMove.getStockMoveLineList());
  }

  @Override
  public Optional<StockMove> copyAndSplitStockMove(
      StockMove stockMove, List<StockMoveLine> stockMoveLines) throws AxelorException {

    stockMoveLines = MoreObjects.firstNonNull(stockMoveLines, Collections.emptyList());
    StockMove newStockMove = stockMoveRepo.copy(stockMove, false);

    for (StockMoveLine stockMoveLine : stockMoveLines) {

      if (stockMoveLine.getQty().compareTo(stockMoveLine.getRealQty()) > 0) {
        StockMoveLine newStockMoveLine = copySplittedStockMoveLine(stockMoveLine);
        newStockMove.addStockMoveLineListItem(newStockMoveLine);
      }
    }

    if (ObjectUtils.isEmpty(newStockMove.getStockMoveLineList())) {
      return Optional.empty();
    }

    newStockMove.setRealDate(null);
    newStockMove.setStockMoveSeq(
        stockMoveToolService.getSequenceStockMove(
            newStockMove.getTypeSelect(), newStockMove.getCompany()));
    newStockMove.setName(
        stockMoveToolService.computeName(
            newStockMove,
            newStockMove.getStockMoveSeq()
                + " "
                + I18n.get(IExceptionMessage.STOCK_MOVE_7)
                + " "
                + stockMove.getStockMoveSeq()
                + " )"));
    newStockMove.setExTaxTotal(stockMoveToolService.compute(newStockMove));

    plan(newStockMove);
    return Optional.of(stockMoveRepo.save(newStockMove));
  }

  protected StockMoveLine copySplittedStockMoveLine(StockMoveLine stockMoveLine)
      throws AxelorException {
    StockMoveLine newStockMoveLine = stockMoveLineRepo.copy(stockMoveLine, false);

    newStockMoveLine.setQty(stockMoveLine.getQty().subtract(stockMoveLine.getRealQty()));
    newStockMoveLine.setRealQty(newStockMoveLine.getQty());
    return newStockMoveLine;
  }

  @Override
  public Optional<StockMove> copyAndSplitStockMoveReverse(StockMove stockMove, boolean split)
      throws AxelorException {
    return copyAndSplitStockMoveReverse(stockMove, stockMove.getStockMoveLineList(), split);
  }

  @Override
  public Optional<StockMove> copyAndSplitStockMoveReverse(
      StockMove stockMove, List<StockMoveLine> stockMoveLines, boolean split)
      throws AxelorException {

    stockMoveLines = MoreObjects.firstNonNull(stockMoveLines, Collections.emptyList());
    StockMove newStockMove = new StockMove();

    newStockMove.setCompany(stockMove.getCompany());
    newStockMove.setPartner(stockMove.getPartner());
    newStockMove.setFromStockLocation(stockMove.getToStockLocation());
    newStockMove.setToStockLocation(stockMove.getFromStockLocation());
    newStockMove.setEstimatedDate(stockMove.getEstimatedDate());
    newStockMove.setFromAddress(stockMove.getFromAddress());
    if (stockMove.getToAddress() != null) newStockMove.setFromAddress(stockMove.getToAddress());
    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING)
      newStockMove.setTypeSelect(StockMoveRepository.TYPE_OUTGOING);
    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING)
      newStockMove.setTypeSelect(StockMoveRepository.TYPE_INCOMING);
    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INTERNAL)
      newStockMove.setTypeSelect(StockMoveRepository.TYPE_INTERNAL);
    newStockMove.setStockMoveSeq(
        stockMoveToolService.getSequenceStockMove(
            newStockMove.getTypeSelect(), newStockMove.getCompany()));

    for (StockMoveLine stockMoveLine : stockMoveLines) {

      if (!split || stockMoveLine.getRealQty().compareTo(stockMoveLine.getQty()) > 0) {
        StockMoveLine newStockMoveLine = stockMoveLineRepo.copy(stockMoveLine, false);

        if (split) {
          newStockMoveLine.setQty(stockMoveLine.getRealQty().subtract(stockMoveLine.getQty()));
          newStockMoveLine.setRealQty(newStockMoveLine.getQty());
        } else {
          newStockMoveLine.setQty(stockMoveLine.getRealQty());
          newStockMoveLine.setRealQty(stockMoveLine.getRealQty());
        }

        newStockMove.addStockMoveLineListItem(newStockMoveLine);
      }
    }

    if (ObjectUtils.isEmpty(newStockMove.getStockMoveLineList())) {
      return Optional.empty();
    }

    newStockMove.setRealDate(null);
    newStockMove.setStockMoveSeq(
        stockMoveToolService.getSequenceStockMove(
            newStockMove.getTypeSelect(), newStockMove.getCompany()));
    newStockMove.setName(
        stockMoveToolService.computeName(
            newStockMove,
            newStockMove.getStockMoveSeq()
                + " "
                + I18n.get(IExceptionMessage.STOCK_MOVE_8)
                + " "
                + stockMove.getStockMoveSeq()
                + " )"));
    newStockMove.setExTaxTotal(stockMoveToolService.compute(newStockMove));
    newStockMove.setIsReversion(true);

    plan(newStockMove);
    return Optional.of(stockMoveRepo.save(newStockMove));
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void cancel(StockMove stockMove, CancelReason cancelReason) throws AxelorException {
    applyCancelReason(stockMove, cancelReason);
    cancel(stockMove);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void cancel(StockMove stockMove) throws AxelorException {
    LOG.debug("Annulation du mouvement de stock : {} ", new Object[] {stockMove.getStockMoveSeq()});

    if (stockMove.getStatusSelect() == StockMoveRepository.STATUS_PLANNED) {
      stockMoveLineService.updateLocations(
          stockMove.getFromStockLocation(),
          stockMove.getToStockLocation(),
          stockMove.getStatusSelect(),
          StockMoveRepository.STATUS_CANCELED,
          stockMove.getPlannedStockMoveLineList(),
          stockMove.getEstimatedDate(),
          false);
    } else {
      stockMoveLineService.updateLocations(
          stockMove.getFromStockLocation(),
          stockMove.getToStockLocation(),
          stockMove.getStatusSelect(),
          StockMoveRepository.STATUS_CANCELED,
          stockMove.getStockMoveLineList(),
          stockMove.getEstimatedDate(),
          true);
    }

    stockMove.setRealDate(appBaseService.getTodayDate());

    stockMove.clearPlannedStockMoveLineList();
    if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING
        && stockMove.getStatusSelect() == StockMoveRepository.STATUS_REALIZED) {
      partnerProductQualityRatingService.undoCalculation(stockMove);
    }

    stockMove.setStatusSelect(StockMoveRepository.STATUS_CANCELED);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Boolean splitStockMoveLinesUnit(List<StockMoveLine> stockMoveLines, BigDecimal splitQty) {

    Boolean selected = false;

    for (StockMoveLine moveLine : stockMoveLines) {
      if (moveLine.isSelected()) {
        selected = true;
        StockMoveLine line = stockMoveLineRepo.find(moveLine.getId());
        BigDecimal totalQty = line.getQty();
        LOG.debug("Move Line selected: {}, Qty: {}", line, totalQty);
        while (splitQty.compareTo(totalQty) < 0) {
          totalQty = totalQty.subtract(splitQty);
          StockMoveLine newLine = stockMoveLineRepo.copy(line, false);
          newLine.setQty(splitQty);
          newLine.setRealQty(splitQty);
          newLine.setStockMove(line.getStockMove());
          stockMoveLineRepo.save(newLine);
        }
        LOG.debug("Qty remains: {}", totalQty);
        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
          StockMoveLine newLine = stockMoveLineRepo.copy(line, false);
          newLine.setQty(totalQty);
          newLine.setRealQty(totalQty);
          stockMoveLineRepo.save(newLine);
          LOG.debug("New line created: {}", newLine);
        }
        stockMoveLineRepo.remove(line);
      }
    }

    return selected;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void splitStockMoveLinesSpecial(
      StockMove stockMove, List<StockMoveLine> stockMoveLines, BigDecimal splitQty) {

    LOG.debug("SplitQty: {}", splitQty);

    for (StockMoveLine moveLine : stockMoveLines) {
      LOG.debug("Move line: {}", moveLine);
      BigDecimal totalQty = moveLine.getQty();
      while (splitQty.compareTo(totalQty) < 0) {
        totalQty = totalQty.subtract(splitQty);
        StockMoveLine newLine = stockMoveLineRepo.copy(moveLine, false);
        newLine.setQty(splitQty);
        newLine.setRealQty(splitQty);
        stockMove.addStockMoveLineListItem(newLine);
      }
      LOG.debug("Qty remains: {}", totalQty);
      if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
        StockMoveLine newLine = stockMoveLineRepo.copy(moveLine, false);
        newLine.setQty(totalQty);
        newLine.setRealQty(totalQty);
        stockMove.addStockMoveLineListItem(newLine);
        LOG.debug("New line created: {}", newLine);
      }
      stockMove.removeStockMoveLineListItem(moveLine);
    }
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public StockMove splitInto2(
      StockMove originalStockMove, List<StockMoveLine> modifiedStockMoveLines)
      throws AxelorException {

    // Copy this stock move
    StockMove newStockMove = stockMoveRepo.copy(originalStockMove, false);
    newStockMove.setStockMoveLineList(new ArrayList<>());

    modifiedStockMoveLines =
        modifiedStockMoveLines
            .stream()
            .filter(stockMoveLine -> stockMoveLine.getQty().compareTo(BigDecimal.ZERO) != 0)
            .collect(Collectors.toList());
    for (StockMoveLine moveLine : modifiedStockMoveLines) {
      StockMoveLine newStockMoveLine;

      // Set quantity in new stock move line
      newStockMoveLine = stockMoveLineRepo.copy(moveLine, false);
      newStockMoveLine.setQty(moveLine.getQty());
      newStockMoveLine.setRealQty(moveLine.getQty());

      // add stock move line
      newStockMove.addStockMoveLineListItem(newStockMoveLine);

      // find the original move line to update it
      Optional<StockMoveLine> correspondingMoveLine =
          originalStockMove
              .getStockMoveLineList()
              .stream()
              .filter(stockMoveLine -> stockMoveLine.getId().equals(moveLine.getId()))
              .findFirst();
      if (BigDecimal.ZERO.compareTo(moveLine.getQty()) > 0
          || (correspondingMoveLine.isPresent()
              && moveLine.getQty().compareTo(correspondingMoveLine.get().getRealQty()) > 0)) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.STOCK_MOVE_16),
            originalStockMove);
      }

      if (correspondingMoveLine.isPresent()) {
        // Update quantity in original stock move.
        // If the remaining quantity is 0, remove the stock move line
        BigDecimal remainingQty = correspondingMoveLine.get().getQty().subtract(moveLine.getQty());
        if (BigDecimal.ZERO.compareTo(remainingQty) == 0) {
          // Remove the stock move line
          originalStockMove.removeStockMoveLineListItem(correspondingMoveLine.get());
        } else {
          correspondingMoveLine.get().setQty(remainingQty);
          correspondingMoveLine.get().setRealQty(remainingQty);
        }
      }
    }

    if (!newStockMove.getStockMoveLineList().isEmpty()) {
      newStockMove.setExTaxTotal(stockMoveToolService.compute(newStockMove));
      originalStockMove.setExTaxTotal(stockMoveToolService.compute(originalStockMove));
      return stockMoveRepo.save(newStockMove);
    } else {
      return null;
    }
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void copyQtyToRealQty(StockMove stockMove) {
    for (StockMoveLine line : stockMove.getStockMoveLineList()) line.setRealQty(line.getQty());
    stockMoveRepo.save(stockMove);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Optional<StockMove> generateReversion(StockMove stockMove) throws AxelorException {

    LOG.debug(
        "Creation d'un mouvement de stock inverse pour le mouvement de stock: {} ",
        new Object[] {stockMove.getStockMoveSeq()});

    return copyAndSplitStockMoveReverse(stockMove, false);
  }

  @Override
  public List<Map<String, Object>> getStockPerDate(
      Long locationId, Long productId, LocalDate fromDate, LocalDate toDate) {

    List<Map<String, Object>> stock = new ArrayList<>();

    while (!fromDate.isAfter(toDate)) {
      Double qty = getStock(locationId, productId, fromDate);
      Map<String, Object> dateStock = new HashMap<>();
      dateStock.put("$date", fromDate);
      dateStock.put("$qty", new BigDecimal(qty));
      stock.add(dateStock);
      fromDate = fromDate.plusDays(1);
    }

    return stock;
  }

  private Double getStock(Long locationId, Long productId, LocalDate date) {

    List<StockMoveLine> inLines =
        stockMoveLineRepo
            .all()
            .filter(
                "self.product.id = ?1 AND self.stockMove.toStockLocation.id = ?2 AND self.stockMove.statusSelect != ?3 AND (self.stockMove.estimatedDate <= ?4 OR self.stockMove.realDate <= ?4)",
                productId,
                locationId,
                StockMoveRepository.STATUS_CANCELED,
                date)
            .fetch();

    List<StockMoveLine> outLines =
        stockMoveLineRepo
            .all()
            .filter(
                "self.product.id = ?1 AND self.stockMove.fromStockLocation.id = ?2 AND self.stockMove.statusSelect != ?3 AND (self.stockMove.estimatedDate <= ?4 OR self.stockMove.realDate <= ?4)",
                productId,
                locationId,
                StockMoveRepository.STATUS_CANCELED,
                date)
            .fetch();

    Double inQty =
        inLines.stream().mapToDouble(inl -> Double.parseDouble(inl.getQty().toString())).sum();

    Double outQty =
        outLines.stream().mapToDouble(out -> Double.parseDouble(out.getQty().toString())).sum();

    Double qty = inQty - outQty;

    return qty;
  }

  @Override
  public List<StockMoveLine> changeConformityStockMove(StockMove stockMove) {
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

    if (stockMoveLineList != null) {
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        stockMoveLine.setConformitySelect(stockMove.getConformitySelect());
      }
    }

    return stockMoveLineList;
  }

  @Override
  public Integer changeConformityStockMoveLine(StockMove stockMove) {
    Integer stockMoveConformitySelect;
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

    if (stockMoveLineList != null) {
      stockMoveConformitySelect = StockMoveRepository.CONFORMITY_COMPLIANT;

      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        Integer conformitySelect = stockMoveLine.getConformitySelect();

        if (!conformitySelect.equals(StockMoveRepository.CONFORMITY_COMPLIANT)) {
          stockMoveConformitySelect = conformitySelect;
          if (conformitySelect.equals(StockMoveRepository.CONFORMITY_NON_COMPLIANT)) {
            break;
          }
        }
      }
    } else {
      stockMoveConformitySelect = StockMoveRepository.CONFORMITY_NONE;
    }

    stockMove.setConformitySelect(stockMoveConformitySelect);
    return stockMoveConformitySelect;
  }

  @Override
  public Map<String, Object> viewDirection(StockMove stockMove) throws AxelorException {

    String fromAddressStr = stockMove.getFromAddressStr();
    String toAddressStr = stockMove.getToAddressStr();

    String dString;
    String aString;
    BigDecimal dLat = BigDecimal.ZERO;
    BigDecimal dLon = BigDecimal.ZERO;
    BigDecimal aLat = BigDecimal.ZERO;
    BigDecimal aLon = BigDecimal.ZERO;
    if (Strings.isNullOrEmpty(fromAddressStr)) {
      Address fromAddress = stockMove.getCompany().getAddress();
      dString = fromAddress.getAddressL4() + " ," + fromAddress.getAddressL6();
      dLat = fromAddress.getLatit();
      dLon = fromAddress.getLongit();
    } else {
      dString = fromAddressStr.replace('\n', ' ');
    }
    if (toAddressStr == null) {
      Address toAddress = stockMove.getCompany().getAddress();
      aString = toAddress.getAddressL4() + " ," + toAddress.getAddressL6();
      aLat = toAddress.getLatit();
      aLon = toAddress.getLongit();
    } else {
      aString = toAddressStr.replace('\n', ' ');
    }
    if (Strings.isNullOrEmpty(dString) || Strings.isNullOrEmpty(aString)) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.STOCK_MOVE_11));
    }
    if (appBaseService.getAppBase().getMapApiSelect()
        == AppBaseRepository.MAP_API_OPEN_STREET_MAP) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_12));
    }
    Map<String, Object> result =
        Beans.get(MapService.class).getDirectionMapGoogle(dString, dLat, dLon, aString, aLat, aLon);
    if (result == null) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.TYPE_FUNCTIONNAL,
          I18n.get(IExceptionMessage.STOCK_MOVE_13),
          dString,
          aString);
    }
    return result;
  }

  @Override
  public String printStockMove(
      StockMove stockMove, List<Integer> lstSelectedMove, String reportType)
      throws AxelorException {
    List<Long> selectedStockMoveListId;
    if (lstSelectedMove != null && !lstSelectedMove.isEmpty()) {
      selectedStockMoveListId =
          lstSelectedMove
              .stream()
              .map(integer -> Long.parseLong(integer.toString()))
              .collect(Collectors.toList());
      stockMove = stockMoveRepo.find(selectedStockMoveListId.get(0));
    } else if (stockMove != null && stockMove.getId() != null) {
      selectedStockMoveListId = new ArrayList<>();
      selectedStockMoveListId.add(stockMove.getId());
    } else {
      throw new AxelorException(
          StockMove.class,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.STOCK_MOVE_10));
    }

    List<StockMove> stockMoveList =
        stockMoveRepo
            .all()
            .filter(
                "self.id IN ("
                    + selectedStockMoveListId
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","))
                    + ") AND self.printingSettings IS NULL")
            .fetch();
    if (!stockMoveList.isEmpty()) {
      String exceptionMessage =
          String.format(
              I18n.get(IExceptionMessage.STOCK_MOVES_MISSING_PRINTING_SETTINGS),
              "<ul>"
                  + stockMoveList
                      .stream()
                      .map(StockMove::getStockMoveSeq)
                      .collect(Collectors.joining("</li><li>", "<li>", "</li>"))
                  + "<ul>");
      throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, exceptionMessage);
    }

    String stockMoveIds =
        selectedStockMoveListId.stream().map(Object::toString).collect(Collectors.joining(","));

    String title = I18n.get("Stock move");
    if (stockMove.getStockMoveSeq() != null) {
      title =
          selectedStockMoveListId.size() == 1
              ? I18n.get("StockMove") + " " + stockMove.getStockMoveSeq()
              : I18n.get("StockMove(s)");
    }

    String locale =
        reportType.equals(IReport.PICKING_STOCK_MOVE)
            ? Beans.get(UserService.class).getLanguage()
            : ReportSettings.getPrintingLocale(stockMove.getPartner());

    ReportSettings reportSettings =
        ReportFactory.createReport(reportType, title + "-${date}")
            .addParam("StockMoveId", stockMoveIds)
            .addParam("Locale", locale);

    if (reportType.equals(IReport.CONFORMITY_CERTIFICATE)) {
      reportSettings.toAttach(stockMove);
    }

    return reportSettings.generate().getFileLink();
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateFullySpreadOverLogisticalFormsFlag(StockMove stockMove) {
    stockMove.setFullySpreadOverLogisticalFormsFlag(
        computeFullySpreadOverLogisticalFormsFlag(stockMove));
  }

  protected boolean computeFullySpreadOverLogisticalFormsFlag(StockMove stockMove) {
    return stockMove.getStockMoveLineList() != null
        ? stockMove
            .getStockMoveLineList()
            .stream()
            .allMatch(
                stockMoveLine ->
                    stockMoveLineService.computeFullySpreadOverLogisticalFormLinesFlag(
                        stockMoveLine))
        : true;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void applyCancelReason(StockMove stockMove, CancelReason cancelReason)
      throws AxelorException {
    if (cancelReason == null) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.CANCEL_REASON_MISSING));
    }
    if (!StockMove.class.getCanonicalName().equals(cancelReason.getApplicationType())) {
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.CANCEL_REASON_BAD_TYPE));
    }
    stockMove.setCancelReason(cancelReason);
  }

  @Override
  public void setAvailableStatus(StockMove stockMove) {
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      stockMoveLineService.setAvailableStatus(stockMoveLine);
    }
  }

  @Override
  public void checkExpirationDates(StockMove stockMove) throws AxelorException {
    if (stockMove.getToStockLocation().getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      stockMoveLineService.checkExpirationDates(stockMove);
    }
  }
}

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
package com.axelor.apps.account.db.repo;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.i18n.I18n;
import java.util.List;
import javax.persistence.PersistenceException;

public class MoveLineManagementRepository extends MoveLineRepository {

  @Override
  public void remove(MoveLine entity) {
    if (!entity.getMove().getStatusSelect().equals(MoveRepository.STATUS_NEW)) {
      throw new PersistenceException(I18n.get(IExceptionMessage.MOVE_ARCHIVE_NOT_OK));
    } else {
      entity.setArchived(true);
    }
  }

  @Override
  public MoveLine save(MoveLine entity) {
    List<AnalyticMoveLine> analyticMoveLineList = entity.getAnalyticMoveLineList();
    if (analyticMoveLineList != null) {
      for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
        analyticMoveLine.setAccount(entity.getAccount());
        analyticMoveLine.setAccountType(entity.getAccount().getAccountType());
      }
    }
    return super.save(entity);
  }
}

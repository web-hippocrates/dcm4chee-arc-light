/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.mpps.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.BasicMPPSSCP;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceApplicationException;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.mpps.MPPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.persistence.PersistenceException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed(DicomService.class)
class MPPSSCP extends BasicMPPSSCP {
    private static final Logger LOG = LoggerFactory.getLogger(MPPSSCP.class);

    @Inject
    private MPPSService mppsService;

    @Inject
    Event<MPPSContext> mppsEvent;

    @Override
    protected Attributes create(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp)
            throws DicomServiceException {
        MPPSContext ctx = mppsService.newMPPSContext(as);
        ctx.setDimse(Dimse.N_CREATE_RQ);
        ctx.setSopInstanceUID(rsp.getString(Tag.AffectedSOPInstanceUID));
        ctx.setAttributes(rqAttrs);
        try {
            ctx.setMPPS(mppsService.createMPPS(ctx));
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            DicomServiceException dse;
            try {
                mppsService.findMPPS(ctx);
                dse = new DicomServiceApplicationException(Status.DuplicateSOPinstance, "duplicate SOP Instance", false);
            } catch (Exception e1) {
                dse = new DicomServiceException(Status.ProcessingFailure, e);
            }
            dse.setUID(Tag.AffectedSOPClassUID, UID.ModalityPerformedProcedureStep);
            dse.setUID(Tag.AffectedSOPInstanceUID, rsp.getString(Tag.AffectedSOPInstanceUID));
            throw dse;
        }
        fire(ctx);
        return null;
    }

    @Override
    protected Attributes set(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp)
            throws DicomServiceException {
        MPPSContext ctx = mppsService.newMPPSContext(as);
        ctx.setDimse(Dimse.N_SET_RQ);
        ctx.setSopInstanceUID(rq.getString(Tag.RequestedSOPInstanceUID));
        ctx.setAttributes(rqAttrs);
        try {
            ctx.setMPPS(mppsService.updateMPPS(ctx));
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (PersistenceException e) {
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        }
        fire(ctx);
        return null;
   }

    private void fire(MPPSContext ctx) {
        try {
            mppsEvent.fire(ctx);
        } catch (Exception e) {
            LOG.warn("Failed on firing MPPS context :\n", e);
        }
    }
}

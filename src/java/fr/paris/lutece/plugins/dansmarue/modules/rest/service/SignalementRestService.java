/*
 * Copyright (c) 2002-2021, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.dansmarue.modules.rest.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.sun.jersey.core.header.FormDataContentDisposition;

import fr.paris.lutece.plugins.dansmarue.business.entities.Adresse;
import fr.paris.lutece.plugins.dansmarue.business.entities.Arrondissement;
import fr.paris.lutece.plugins.dansmarue.business.entities.MessageTypologie;
import fr.paris.lutece.plugins.dansmarue.business.entities.NotificationSignalementUserMultiContents;
import fr.paris.lutece.plugins.dansmarue.business.entities.ObservationRejet;
import fr.paris.lutece.plugins.dansmarue.business.entities.Photo;
import fr.paris.lutece.plugins.dansmarue.business.entities.PhotoDMR;
import fr.paris.lutece.plugins.dansmarue.business.entities.Priorite;
import fr.paris.lutece.plugins.dansmarue.business.entities.Signalement;
import fr.paris.lutece.plugins.dansmarue.business.entities.SignalementGeoLoc;
import fr.paris.lutece.plugins.dansmarue.business.entities.Signaleur;
import fr.paris.lutece.plugins.dansmarue.business.entities.SiraUser;
import fr.paris.lutece.plugins.dansmarue.business.entities.Source;
import fr.paris.lutece.plugins.dansmarue.business.entities.TypeSignalement;
import fr.paris.lutece.plugins.dansmarue.business.exceptions.AlreadyFollowedException;
import fr.paris.lutece.plugins.dansmarue.business.exceptions.InvalidStateActionException;
import fr.paris.lutece.plugins.dansmarue.business.exceptions.NonExistentFollowItem;
import fr.paris.lutece.plugins.dansmarue.modules.rest.dto.SignalementRestDTO;
import fr.paris.lutece.plugins.dansmarue.modules.rest.pojo.ErrorSignalement;
import fr.paris.lutece.plugins.dansmarue.modules.rest.pojo.SignalementPOJO;
import fr.paris.lutece.plugins.dansmarue.modules.rest.pojo.SignalementsPOJO;
import fr.paris.lutece.plugins.dansmarue.modules.rest.service.formatters.CategoriesFormatterJson;
import fr.paris.lutece.plugins.dansmarue.modules.rest.service.formatters.ErrorSignalementFormatterJson;
import fr.paris.lutece.plugins.dansmarue.modules.rest.service.formatters.SignalementFormatterJson;
import fr.paris.lutece.plugins.dansmarue.modules.rest.service.formatters.SignalementRestDTOFormatterJson;
import fr.paris.lutece.plugins.dansmarue.modules.rest.util.constants.SignalementRestConstants;
import fr.paris.lutece.plugins.dansmarue.modules.rest.util.exception.ParseSignalementFromJSONException;
import fr.paris.lutece.plugins.dansmarue.service.IAdresseService;
import fr.paris.lutece.plugins.dansmarue.service.IArrondissementService;
import fr.paris.lutece.plugins.dansmarue.service.IMessageTypologieService;
import fr.paris.lutece.plugins.dansmarue.service.IObservationRejetService;
import fr.paris.lutece.plugins.dansmarue.service.IPhotoService;
import fr.paris.lutece.plugins.dansmarue.service.IPrioriteService;
import fr.paris.lutece.plugins.dansmarue.service.ISignalementService;
import fr.paris.lutece.plugins.dansmarue.service.ISignaleurService;
import fr.paris.lutece.plugins.dansmarue.service.ISiraUserService;
import fr.paris.lutece.plugins.dansmarue.service.ITypeSignalementService;
import fr.paris.lutece.plugins.dansmarue.service.IWorkflowService;
import fr.paris.lutece.plugins.dansmarue.service.dto.DossierSignalementDTO;
import fr.paris.lutece.plugins.dansmarue.service.dto.TypeSignalementDTO;
import fr.paris.lutece.plugins.dansmarue.service.output.SignalementOutputPrcessor;
import fr.paris.lutece.plugins.dansmarue.util.constants.SignalementConstants;
import fr.paris.lutece.plugins.dansmarue.utils.DateUtils;
import fr.paris.lutece.plugins.dansmarue.utils.ImgUtils;
import fr.paris.lutece.plugins.identitystore.v1.web.rs.dto.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v1.web.service.IdentityService;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityNotFoundException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.leaflet.modules.dansmarue.entities.Address;
import fr.paris.lutece.plugins.leaflet.modules.dansmarue.service.IAddressSuggestPOIService;
import fr.paris.lutece.plugins.rest.service.formatters.IFormatter;
import fr.paris.lutece.plugins.unittree.modules.dansmarue.business.sector.Sector;
import fr.paris.lutece.plugins.workflowcore.business.action.Action;
import fr.paris.lutece.plugins.workflowcore.business.action.ActionFilter;
import fr.paris.lutece.plugins.workflowcore.business.resource.ResourceHistory;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.service.action.IActionService;
import fr.paris.lutece.plugins.workflowcore.service.state.IStateService;
import fr.paris.lutece.plugins.workflowcore.service.task.ITask;
import fr.paris.lutece.plugins.workflowcore.service.task.ITaskService;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.service.datastore.DatastoreService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.image.ImageResource;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.service.workflow.WorkflowService;
import fr.paris.lutece.util.image.ImageUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * SignalementRestService.
 */
public class SignalementRestService implements ISignalementRestService
{

    /** The Constant PARAMETER_NULL. */
    private static final String PARAMETER_NULL = "null";

    /** The signalement service. */
    // SERVICES
    @Inject
    private ISignalementService _signalementService;

    /** The adresse service. */
    @Inject
    private IAdresseService _adresseService;

    /** The arrondissement service. */
    @Inject
    private IArrondissementService _arrondissementService;

    /** The photo service. */
    @Inject
    private IPhotoService _photoService;

    /** The signaleur service. */
    @Inject
    private ISignaleurService _signaleurService;

    /** The signalement workflow service. */
    @Inject
    private IWorkflowService _signalementWorkflowService;

    /** The observation rejet service. */
    @Inject
    private IObservationRejetService _observationRejetService;

    /** The type signalement service. */
    @Inject
    private ITypeSignalementService _typeSignalementService;

    /** The priorite service. */
    @Inject
    private IPrioriteService _prioriteService;

    /** The manage signalement service. */
    @Inject
    private ManageSignalementService _manageSignalementService;

    /** The sira user service. */
    @Inject
    private ISiraUserService _siraUserService;

    /** The task service. */
    @Inject
    private ITaskService _taskService;

    /** The action service. */
    @Inject
    private IActionService _actionService;

    /** The state service. */
    @Inject
    private IStateService _stateService;

    /** The address suggest POI service. */
    @Inject
    private IAddressSuggestPOIService _addressSuggestPOIService;

    /** The message typologie service. */
    @Inject
    private IMessageTypologieService _messageTypologieService;

    /** The Constant PARAMETER_WEBSERVICE_COMMENT_VALUE. */
    private static final String PARAMETER_WEBSERVICE_COMMENT_VALUE = "webservice_comment_value";

    /** The Constant ID_STATE_ETAT_INITIAL. */
    private static final String ID_STATE_ETAT_INITIAL = "signalement.idStateEtatInitial";

    /** The Constant PARAMETER_WEBSERVICE_CHOSEN_MESSAGE. */
    private static final String PARAMETER_WEBSERVICE_CHOSEN_MESSAGE = "chosenMessage";

    /** The Constant PARAMETER_WEBSERVICE_IS_MESSAGE_TYPO. */
    private static final String PARAMETER_WEBSERVICE_IS_MESSAGE_TYPO = "isMessageTypo";

    /** The base 64. */
    private static final String BASE_64 = ";base64,";

    /**
     * {@inheritDoc}
     */
    @Override
    public String processResquestAnswer( String strRequest, HttpServletRequest request ) throws ParseSignalementFromJSONException
    {
        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );

        try
        {
            JSONArray jsonArray = (JSONArray) JSONSerializer.toJSON( strRequest );
            JSONObject json = jsonArray.getJSONObject( 0 );
            String strRequestType = json.getString( SignalementRestConstants.JSON_TAG_REQUEST );
            AppLogService.debug( "module-signalement-rest processResquestAnswer" + strRequestType );
            String strRespons;

            if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_INCIDENT_STATS ) )
            {
                strRespons = getIncidentStats( json, request );
            }
            else
                if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_INCIDENTS_BY_ID ) )
                {
                    strRespons = getIncidentsById( json );
                }
                else
                    if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_INCIDENT_BY_POSITION ) )
                    {
                        strRespons = getIncidentsByPosition( json );
                    }
                    else
                        if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_SAVE_INCIDENT ) )
                        {
                            strRespons = saveIncident( json );
                        }
                        else
                            if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_REPORTS ) )
                            {
                                strRespons = getReports( json );
                            }
                            else
                                if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_UPDATE_INCIDENT ) )
                                {
                                    strRespons = updateIncident( json, request );
                                }
                                else
                                    if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_USER_ACTIVITIES ) )
                                    {
                                        strRespons = getUsersActivities( json );
                                    }
                                    else
                                        if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_CHANGE_INCIDENT ) )
                                        {
                                            strRespons = changeIncident( json );
                                        }
                                        else
                                            if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_INCIDENT_PHOTOS ) )
                                            {
                                                strRespons = getIncidentPhotos( json );
                                            }
                                            else
                                                if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_CATEGORIES_LIST ) )
                                                {
                                                    strRespons = getCategoriesList( json );
                                                }
                                                else
                                                    if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_CHANGE_STATUS ) )
                                                    {
                                                        strRespons = changeStatus( json, request );
                                                    }
                                                    else
                                                        if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_ADD_ANOMALIE ) )
                                                        {
                                                            strRespons = addAnomalie( json );
                                                        }
                                                        else
                                                            if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_CONGRATULATE_ANOMALIE ) )
                                                            {
                                                                strRespons = congratulateAnomalie( json );
                                                            }
                                                            else
                                                                if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_INCIDENT_RESOLVED ) )
                                                                {
                                                                    strRespons = setIncidentResolved( json, request );
                                                                }
                                                                else
                                                                    if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_FOLLOW ) )
                                                                    {
                                                                        strRespons = addFollower( json );
                                                                    }
                                                                    else
                                                                        if ( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_UNFOLLOW ) )
                                                                        {
                                                                            strRespons = removeFollower( json );
                                                                        }
                                                                        else
                                                                            if ( strRequestType
                                                                                    .equals( SignalementRestConstants.REQUEST_TYPE_GET_INCIDENTS_BY_USER ) )
                                                                            {
                                                                                strRespons = getIncidentsByUser( json );
                                                                            }
                                                                            else
                                                                                if ( strRequestType
                                                                                        .equals( SignalementRestConstants.REQUEST_TYPE_PROCESS_WORKFLOW ) )
                                                                                {
                                                                                    strRespons = processWorkflow( json );
                                                                                }
                                                                                else
                                                                                    if ( strRequestType
                                                                                            .equals( SignalementRestConstants.REQUEST_TYPE_CHECK_VERSION ) )
                                                                                    {
                                                                                        strRespons = checkVersion( );
                                                                                    }
                                                                                    else if( strRequestType.equals( SignalementRestConstants.REQUEST_TYPE_IS_MAIL_AGENT ) )
                                                                                    {
                                                                                        strRespons = checkMailAgent( json );
                                                                                    }
                                                                                    else
                                                                                    {
                                                                                        ErrorSignalement error = new ErrorSignalement( );
                                                                                        error.setErrorCode( SignalementRestConstants.ERROR_BAD_JSON_REQUEST );
                                                                                        error.setErrorMessage( StringUtils.EMPTY );

                                                                                        return formatterJson.format( error );
                                                                                    }

            JSONArray jsonArrayRespons = new JSONArray( );
            jsonArrayRespons.element( strRespons );

            return jsonArrayRespons.toString( );
        }
        catch( JSONException e )
        {
            AppLogService.info( request.getHeader( "host" ) );
            AppLogService.info( request.getHeader( "user-agent" ) );
            AppLogService.info( request.getHeader( "origin" ) );
            AppLogService.info( strRequest );
            AppLogService.error( e.getMessage( ), e );

            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_JSON_REQUEST );
            error.setErrorMessage( e.getMessage( ) );

            return formatterJson.format( error );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIncidentStats( JSONObject jsonSrc, HttpServletRequest request )
    {
        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _signalementWorkflowService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_WORKFLOW_BEAN );

        JSONObject jsonPosition = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_POSITION );
        String strLatitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LATITUDE );
        String strLongitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LONGITUDE );
        String strRadius = AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_RADIUS );

        if ( StringUtils.isBlank( strLatitude ) || StringUtils.isBlank( strLongitude ) || StringUtils.isBlank( strRadius ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_POSITION_PARAMETER );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_INCIDENT_STATS );

        JSONObject json = new JSONObject( );

        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        List<Signalement> listSignalement = _signalementService.findAllSignalementInPerimeterWithInfo( Double.parseDouble( strLatitude ),
                Double.parseDouble( strLongitude ), Integer.valueOf( strRadius ) );

        AppLogService.info( "module-signalement-rest getIncidentStats" );
        int nOnGoingIncidents = 0;
        int nResolvedIncidents = 0;
        int nUpdatedIncidents = 0;

        // set the state of the signalement with the workflow
        WorkflowService workflowService = WorkflowService.getInstance( );

        if ( workflowService.isAvailable( ) )
        {
            // récupération de l'identifiant du workflow
            Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

            if ( workflowId != null )
            {
                classifiedByState( listSignalement, listSignalement, listSignalement, workflowService, workflowId, listSignalement );
            }
        }

        json.accumulate( SignalementRestConstants.JSON_TAG_ONGOING_INCIDENTS, nOnGoingIncidents );
        json.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_INCIDENTS, nResolvedIncidents );
        json.accumulate( SignalementRestConstants.JSON_TAG_UPDATED_INCIDENTS, nUpdatedIncidents );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    private void classifiedByState( List<Signalement> listDeclaredIncidents, List<Signalement> listResolvedIncidents, List<Signalement> listUpdatedIncidents,
            WorkflowService workflowService, Integer workflowId, List<Signalement> listSignalement )
    {

        for ( Signalement signalement : listSignalement )
        {
            State state = workflowService.getState( signalement.getId( ).intValue( ), Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null );

            int nStateId = state.getId( );

            switch( nStateId )
            {
                case 7:
                    listDeclaredIncidents.add( signalement );

                    break;

                case 8:
                    listUpdatedIncidents.add( signalement );

                    break;

                case 9:
                    listUpdatedIncidents.add( signalement );

                    break;

                case 10:
                    listResolvedIncidents.add( signalement );

                    break;

                case 11:
                    break;

                case 12:
                    listResolvedIncidents.add( signalement );

                    break;

                case 13:
                    listDeclaredIncidents.add( signalement );

                    break;

                default:
                    break;
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIncidentsById( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ID );

        if ( StringUtils.isBlank( strIncidentId ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        String source = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_SOURCE );
        String sourceDMR = AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_INCIDENT_SOURCE_DMR );
        if ( !StringUtils.equals( source, sourceDMR ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_SOURCE );
            error.setErrorMessage( "Source " + source + " du signalement inconnue. " );
            AppLogService.error( "Source " + source + " du signalement inconnue. " );
            return formatterJsonError.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_INCIDENTS_BY_ID );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        Signalement signalement = _signalementService.getSignalement( Long.parseLong( strIncidentId ) );

        if ( signalement == null )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        // Si le signaleur n'est pas trouvé et que l'email ne finit pas par @paris.fr, cache le bouton résolu sur mobile
        String guid = StringUtils.EMPTY;
        try
        {
            guid = jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        SignalementFormatterJson formatterJson = new SignalementFormatterJson( );
        if ( !StringUtils.isBlank( guid ) )
        {
            formatterJson.setFormatWithGuid( guid );
        }

        json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENT, formatterJson.format( signalement ) );

        List<Signaleur> signaleurs = signalement.getSignaleurs( );

        boolean signaleurFound = false;

        for ( Signaleur signaleur : signaleurs )
        {
            if ( StringUtils.isNotBlank( guid ) && guid.equals( signaleur.getGuid( ) ) )
            {
                signaleurFound = true;
            }
        }

        if ( !StringUtils.EMPTY.equals( guid ) )
        {
            json.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_AUTHORIZATION,
                    signaleurFound || StringUtils.endsWithAny( getIdentityStoreAttributeValue( guid, "login" ).toLowerCase( ), getEmailDomainAccept( ) ) );
        }
        else
        {
            json.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_AUTHORIZATION, false );
        }

        json.accumulate( SignalementRestConstants.JSON_TAG_MESSAGE_SF_GENERIC, getMessageServiceFaitGeneric( signalement.getId( ) ) );

        json.accumulate( SignalementRestConstants.JSON_TAG_MESSAGE_SF_TYPOLOGIE, getMessageServiceFaitTypologie( signalement.getTypeSignalement( ).getId( ) ) );

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings( "static-access" )
    public String getIncidentsByPosition( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_INCIDENT_BY_POSITION );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        JSONObject jsonPosition = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_POSITION );
        String strLatitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LATITUDE );
        String strLongitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LONGITUDE );
        String strRadius = AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_RADIUS );
        String guid = jsonSrc.has( SignalementRestConstants.JSON_TAG_GUID ) ? jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID ) : null;

        if ( StringUtils.isBlank( strLatitude ) || StringUtils.isBlank( strLongitude ) || StringUtils.isBlank( strRadius ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_POSITION_PARAMETER );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        List<Signalement> listSignalement = _signalementService.findAllSignalementInPerimeterWithInfo( Double.parseDouble( strLatitude ),
                Double.parseDouble( strLongitude ), Integer.valueOf( strRadius ) );

        List<Signalement> listSignalementSorted = getSignalementListSorted( Double.parseDouble( strLatitude ), Double.parseDouble( strLongitude ),
                listSignalement );

        SignalementFormatterJson formatterJson = new SignalementFormatterJson( );
        if ( !StringUtils.isBlank( guid ) )
        {
            formatterJson.setFormatWithGuid( guid );
        }

        JSONArray jsonArray = (JSONArray) JSONSerializer.toJSON( formatterJson.format( listSignalementSorted ) );

        json.accumulate( SignalementRestConstants.JSON_TAG_CLOSEST_INCIDENTS, jsonArray.toString( ) );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Covert a json report to report Object.
     *
     * @param jsonSrc
     *            the json source
     * @param signalement
     *            the report object
     * @param signaleur
     *            person who create the report
     * @param adresse
     *            the report address
     * @return the report
     *
     * @throws ParseSignalementFromJSONException
     *             parse exception
     */
    public Signalement parseSignalement( JSONObject jsonSrc, Signalement signalement, Signaleur signaleur, Adresse adresse )
            throws ParseSignalementFromJSONException
    {
        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );
        String strUDID = jsonSrc.getString( SignalementRestConstants.JSON_TAG_UDID );
        String strEmail = StringUtils.EMPTY;
        try
        {
            strEmail = jsonSrc.getString( SignalementRestConstants.JSON_TAG_EMAIL );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
            strEmail = AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_DEFAULT_EMAIL, "lutece@lutece.fr" );
        }

        String guid = StringUtils.EMPTY;
        try
        {
            guid = jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        if ( StringUtils.isBlank( strUDID ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }
        JSONObject jsonIncident = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_INCIDENT );
        String strCategoryId = jsonIncident.getString( SignalementRestConstants.JSON_TAG_INCIDENT_CATEGORIE_ID );
        String strAddress = jsonIncident.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ADDRESS );
        String strDescriptive = jsonIncident.getString( SignalementRestConstants.JSON_TAG_INCIDENT_DESCRIPTIVE );
        String strCommentaireAgentTerrain = getStringIfExist( jsonIncident, SignalementRestConstants.JSON_TAG_INCIDENT_COMMENTAIRE_AGENT );
        long lIdPriorite = jsonIncident.getLong( SignalementRestConstants.JSON_TAG_INCIDENT_PRIORITE_ID );
        AppLogService.info( "saveIncident, lIdPriorite " + lIdPriorite );

        JSONObject jsonPosition = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_POSITION );
        String strLatitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LATITUDE );
        String strLongitude = jsonPosition.getString( SignalementRestConstants.JSON_TAG_LONGITUDE );

        if ( StringUtils.isBlank( strLatitude ) && StringUtils.isBlank( strLongitude ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_POSITION_PARAMETER );
            error.setErrorMessage( StringUtils.EMPTY );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }

        // Catégorie ou Type du signalement
        if ( StringUtils.isBlank( strCategoryId ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_CATEGORY_ID_PARAMETER );
            error.setErrorMessage( StringUtils.EMPTY );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }

        TypeSignalement typeSignalement = _typeSignalementService.findByIdTypeSignalement( Integer.parseInt( strCategoryId ) );

        if ( typeSignalement == null )
        {
            AppLogService.error( "saveIncident, aucune catégorie trouvé pour l'strCategoryId = " + strCategoryId );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_CATEGORY_ID_PARAMETER );
            error.setErrorMessage( "aucune catégorie trouvé pour l'strCategoryId = " + strCategoryId );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }
        AppLogService.info( "saveIncident, category found " + typeSignalement.getFormatTypeSignalement( ) );

        signalement.setTypeSignalement( typeSignalement );

        // Commentaire
        signalement.setCommentaire( strDescriptive );

        // Commentaire agent
        signalement.setCommentaireAgentTerrain( strCommentaireAgentTerrain );

        // Nombre de personnes ayant suivi l'incident: initialisation à 0
        signalement.setSuivi( 0 );

        // La priorité n'est pas encore renseigner du coté de l'API android (valeur par défaut à 1)
        Priorite priorite = _prioriteService.load( lIdPriorite );
        if ( priorite == null )
        {
            AppLogService.error( "saveIncident, no priorite founded " );
            priorite = new Priorite( );
            priorite.setId( 1 );
        }
        signalement.setPriorite( priorite );
        AppLogService.info( "saveIncident, priorite found " + priorite.getLibelle( ) );

        // Arrondissement
        Arrondissement arrondissementByGeom = _adresseService.getArrondissementByGeom( Double.parseDouble( strLongitude ), Double.parseDouble( strLatitude ) );
        if ( arrondissementByGeom == null )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_POSITION_PARAMETER );
            error.setErrorMessage( "l'arrondissement n'est pas trouvé pour cette latitude et longitude. Etes vous bien dans Paris ? lat,lng : " + strLatitude
                    + "," + strLongitude );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }
        signalement.setArrondissement( arrondissementByGeom );
        AppLogService.info( "saveIncident, arrondissementByGeom found " + arrondissementByGeom.getNumero( ) );

        // Secteur
        Sector secteur = _adresseService.getSecteurByGeomAndTypeSignalement( Double.parseDouble( strLongitude ), Double.parseDouble( strLatitude ),
                Integer.parseInt( strCategoryId ) );
        if ( secteur == null )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_POSITION_PARAMETER );
            error.setErrorMessage(
                    "le secteur auquel attribuer cette anomalie n'a pas été trouvé. Si vous êtes bien dans Paris, il s'agit d'un problème de configuration du serveur. lat,lng,cat : "
                            + strLatitude + "," + strLongitude + "," + strCategoryId );
            AppLogService.error(
                    "le secteur auquel attribuer ce message n'a pas été trouvé. Si vous êtes bien dans Paris, il s'agit d'un problème de configuration du serveur. lat,lng,cat : "
                            + strLatitude + "," + strLongitude + "," + strCategoryId );

            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }

        signalement.setSecteur( secteur );

        // date of creation
        SimpleDateFormat sdfDate = new SimpleDateFormat( DateUtils.DATE_FR );
        String strCurrentDate = sdfDate.format( Calendar.getInstance( ).getTime( ) );
        signalement.setDateCreation( strCurrentDate );

        // récupération de l'année et la lettre correspondant au mois
        Date dateDay = DateUtils.getDate( signalement.getDateCreation( ), false );
        int moisSignalement = DateUtils.getMoisInt( dateDay );
        String strAnnee = DateUtils.getAnnee( Calendar.getInstance( ).getTime( ) );
        signalement.setAnnee( Integer.parseInt( strAnnee ) );
        signalement.setMois( _signalementService.getLetterByMonth( moisSignalement ) );

        // Préfixe signalement
        try
        {
            String strOrigin = jsonIncident.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ORIGIN );
            if ( !SignalementRestConstants.SIGNALEMENT_PREFIXES.contains( strOrigin ) )
            {
                ErrorSignalement error = new ErrorSignalement( );
                error.setErrorCode( SignalementRestConstants.ERROR_BAD_ORIGIN );
                error.setErrorMessage( "Le type d'origine " + strOrigin + " du signalement n'a pas été trouvé. " );
                AppLogService.error( "Le type d'origine " + strOrigin + " du signalement n'a pas été trouvé. " );
                throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
            }
            signalement.setPrefix( strOrigin );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_ORIGIN );
            error.setErrorMessage( "Le type d'origine est obligatoire : le webservice a été appelé a partir d'une app mobile V1" );
            AppLogService.error( "Le type d'origine est obligatoire : le webservice a été appelé a partir d'une app mobile V1" );
            throw new ParseSignalementFromJSONException( formatterJson.format( error ) );
        }

        // Creation of the unique token for the signalement
        _signalementService.affectToken( signalement );

        signaleur.setMail( strEmail );
        signaleur.setIdTelephone( strUDID );
        signaleur.setSignalement( signalement );
        signaleur.setGuid( guid );

        adresse.setLat( Double.parseDouble( strLatitude ) );
        adresse.setLng( Double.parseDouble( strLongitude ) );
        adresse.setAdresse( strAddress );
        signalement.getAdresses( ).add( adresse );

        adresse.setSignalement( signalement );

        return signalement;
    }

    /**
     * Gets the string if exist.
     *
     * @param jsonObject
     *            the json object
     * @param key
     *            the key
     * @return the string if exist
     */
    private String getStringIfExist( JSONObject jsonObject, String key )
    {
        String value = "";
        if ( jsonObject.containsKey( key ) )
        {
            value = jsonObject.getString( key );
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String saveIncident( JSONObject jsonSrc ) throws ParseSignalementFromJSONException
    {
        // Warning, before edit this method, another method can save signalement SignalementJspBean.getSignalementDataAndSave (in plugin-signalement)
        AppLogService.info( "saveIncident BEGINNING" );

        Signalement signalement = new Signalement( );
        Signaleur signaleur = new Signaleur( );
        Adresse adresse = new Adresse( );
        parseSignalement( jsonSrc, signalement, signaleur, adresse );

        // Checking token (if device)
        String strUserToken = StringUtils.EMPTY;
        if ( StringUtils.isNotBlank( signaleur.getGuid( ) ) )
        {
            try
            {
                strUserToken = jsonSrc.getString( SignalementRestConstants.JSON_TAG_USER_TOKEN );
            }
            catch( Exception e )
            {
                AppLogService.error( e );
            }
        }

        // ajout d'un signalement en base et récupération de son id
        Long nId = _signalementService.insert( signalement );
        signalement.setId( nId );
        AppLogService.info( "saveIncident, saved with the ID " + nId );

        _adresseService.insert( adresse );

        // ajout d'un signaleur
        _signaleurService.insert( signaleur );

        int returnCode = 0;

        // Création du signaleur
        if ( StringUtils.isNotBlank( signaleur.getGuid( ) ) )
        {
            SiraUser siraUser = new SiraUser( );
            siraUser.setDevice( signalement.getPrefix( ) );
            siraUser.setGuid( signaleur.getGuid( ) );
            siraUser.setToken( strUserToken );
            siraUser.setMail( signaleur.getMail( ) );
            siraUser.setUdid( signaleur.getIdTelephone( ) );

            _siraUserService.createUser( siraUser );
        }

        JSONObject jsonObject = new JSONObject( );
        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_STATUS, returnCode );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID, nId );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_SAVE_INCIDENT );

        AppLogService.info( "saveIncident, OK " + signalement.getId( ) );
        return jsonObject.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReports( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _signalementWorkflowService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_WORKFLOW_BEAN );

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        JSONObject jsonAnswer = new JSONObject( );
        String strUDID = jsonSrc.getString( SignalementRestConstants.JSON_TAG_UDID );

        if ( StringUtils.isNotBlank( strUDID ) )
        {
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_REPORTS );

            JSONObject json = new JSONObject( );

            JSONObject jsonIncidents = new JSONObject( );

            List<Signalement> listSignalement = _signalementService.findSignalementsByIdTelephone( strUDID );

            List<Signalement> listDeclaredIncidents = new ArrayList<>( );
            List<Signalement> listResolvedIncidents = new ArrayList<>( );
            List<Signalement> listUpdatedIncidents = new ArrayList<>( );

            // set the state of the signalement with the workflow
            WorkflowService workflowService = WorkflowService.getInstance( );

            if ( workflowService.isAvailable( ) )
            {
                // récupération de l'identifiant du workflow
                Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

                if ( workflowId != null )
                {
                    classifiedByState( listDeclaredIncidents, listResolvedIncidents, listUpdatedIncidents, workflowService, workflowId, listSignalement );
                }
            }

            json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
            json.accumulate( SignalementRestConstants.JSON_TAG_DECLARED_INCIDENTS, listDeclaredIncidents.size( ) );
            json.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_INCIDENTS, listResolvedIncidents.size( ) );
            json.accumulate( SignalementRestConstants.JSON_TAG_UPDATED_INCIDENTS, listUpdatedIncidents.size( ) );

            IFormatter<Signalement> formatterJson = new SignalementFormatterJson( );
            jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_DECLARED_INCIDENTS, formatterJson.format( listDeclaredIncidents ) );
            jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_INCIDENTS, formatterJson.format( listResolvedIncidents ) );
            jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_UPDATED_INCIDENTS, formatterJson.format( listUpdatedIncidents ) );

            json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENTS, jsonIncidents );

            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

            return jsonAnswer.toString( );
        }
        else
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String updateIncident( JSONObject jsonSrc, HttpServletRequest request )
    {
        _signalementWorkflowService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_WORKFLOW_BEAN );
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        JSONObject jsonIncidentLog = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_INCIDENT_LOG );
        String strIncidentId = jsonIncidentLog.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        String strStatus = jsonIncidentLog.getString( SignalementRestConstants.JSON_TAG_LOG_STATUS );

        WorkflowService workflowService = WorkflowService.getInstance( );
        if ( workflowService.isAvailable( ) )
        {
            // récupération de l'identifiant du workflow
            Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

            if ( workflowId != null )
            {
                if ( strStatus.equals( SignalementRestConstants.UPDATE_STATUS_RESOLVED ) )
                {
                    // 18 ou 22
                    workflowService.doProcessAction( Integer.parseInt( strIncidentId ), Signalement.WORKFLOW_RESOURCE_TYPE, 18, null, request,
                            request.getLocale( ), true );
                }
                else
                    if ( strStatus.equals( SignalementRestConstants.UPDATE_STATUS_INVALID ) )
                    {
                        workflowService.doProcessAction( Integer.parseInt( strIncidentId ), Signalement.WORKFLOW_RESOURCE_TYPE, 16, null, request,
                                request.getLocale( ), true );
                    }
            }
        }

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_UPDATE_INCIDENT );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUsersActivities( JSONObject jsonSrc )
    {
        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_USER_ACTIVITIES );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        JSONArray jsonEmptyArray = new JSONArray( );

        json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENT_LOG, jsonEmptyArray );

        JSONObject jsonIncidents = new JSONObject( );

        jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_RESOLVED_INCIDENTS, jsonEmptyArray );
        jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_ONGOING_INCIDENTS, jsonEmptyArray );
        jsonIncidents.accumulate( SignalementRestConstants.JSON_TAG_UPDATED_INCIDENTS, jsonEmptyArray );

        json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENTS, jsonIncidents );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String changeIncident( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _adresseService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_ADRESSE_SERVICE_BEAN );

        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        String strCategoryId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_CATEGORIE_ID );
        String strAddress = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ADDRESS );

        Signalement signalement = _signalementService.getSignalement( Long.parseLong( strIncidentId ) );

        TypeSignalement typeSignalement = new TypeSignalement( );
        typeSignalement.setId( Integer.parseInt( strCategoryId ) );
        signalement.setTypeSignalement( typeSignalement );

        Adresse adresse = _adresseService.loadByIdSignalement( Long.parseLong( strIncidentId ) );

        adresse.setAdresse( strAddress );
        _adresseService.update( adresse );

        _signalementService.update( signalement );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_CHANGE_INCIDENT );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIncidentPhotos( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );

        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        Signalement signalement = _signalementService.getSignalement( Long.parseLong( strIncidentId ) );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_INCIDENT_PHOTOS );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        JSONArray jsonArrayPhotos = new JSONArray( );
        List<PhotoDMR> pPhotos = _photoService.findBySignalementId( signalement.getId( ) );

        if ( pPhotos != null )
        {
            Collections.reverse( pPhotos );
            for ( PhotoDMR photo : pPhotos )
            {
                JSONObject jsonPhoto = new JSONObject( );
                jsonPhoto.accumulate( SignalementRestConstants.JSON_TAG_PHOTO_URL,
                        AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_URL_PICTURE ) + photo.getId( ) );
                jsonPhoto.accumulate( SignalementRestConstants.JSON_TAG_COMMENTARY, StringUtils.EMPTY );
                jsonPhoto.accumulate( SignalementRestConstants.JSON_TAG_INCIDENT_DATE,
                        fr.paris.lutece.plugins.dansmarue.modules.rest.util.date.DateUtils.convertDate( photo.getDate( ) ) );
                jsonArrayPhotos.element( jsonPhoto );
            }
        }

        json.accumulate( SignalementRestConstants.JSON_TAG_PHOTOS, jsonArrayPhotos );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String changeStatus( JSONObject jsonSrc, HttpServletRequest request )
    {
        JSONObject jsonObject = new JSONObject( );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_CHANGE_STATUS );
        JSONObject jsonAnswer = new JSONObject( );

        if ( !isGoodFormat( jsonSrc ) )
        {
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                    I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_WRONG_FORMAT, request.getLocale( ) ) );
            jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
        }
        else
            if ( !isTypeSignalementSelectable( jsonSrc ) )
            {
                jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                        I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_WRONG_TYPE, request.getLocale( ) ) );
                jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
            }
            else
            {
                try
                {
                    JSONObject answer = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER );

                    int id = answer.getInt( SignalementRestConstants.JSON_TAG_INCIDENT_ID );
                    String status = answer.getString( SignalementRestConstants.JSON_TAG_STATUS );

                    Signalement signalement;

                    signalement = _signalementService.getSignalement( id );

                    if ( signalement == null )
                    {
                        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                                I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_NO_ANOMALY_FOUND, request.getLocale( ) ) );
                        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
                    }
                    else
                    {

                        String comment = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_COMMENT ).equals( PARAMETER_NULL ) ? null
                                : answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_COMMENT );
                        String token = answer.getString( SignalementRestConstants.JSON_TAG_TOKEN );
                        String reference = answer.getString( SignalementRestConstants.JSON_TAG_REFERENCE );
                        String motifRejetAutre;
                        String strDateProgrammee;
                        String chosenMessage = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_CHOSEN_MESSAGE ).equals( PARAMETER_NULL ) ? null
                                : answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_CHOSEN_MESSAGE );
                        String emailActeur = answer.containsKey( SignalementRestConstants.JSON_TAG_EMAIL )
                                ? answer.getString( SignalementRestConstants.JSON_TAG_EMAIL )
                                        : null;

                        String signalementreference = _signalementService.getSignalementReference( StringUtils.EMPTY, signalement );

                        if ( !reference.equals( signalementreference ) )
                        {

                            // NOK la reference ne correspond pas
                            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                                    I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_NO_ANOMALY_FOUND, request.getLocale( ) ) );
                            jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
                        }

                        else
                            if ( token.equals( signalement.getToken( ) ) )
                            {
                                // OK le token correspond

                                if ( controleCoherenceStatus( jsonObject, answer, jsonAnswer, request ) )
                                {
                                    motifRejetAutre = !StringUtils.isEmpty( answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ID_REJET ) )
                                            ? answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ID_REJET )
                                                    : StringUtils.EMPTY;
                                    strDateProgrammee = StringUtils
                                            .isNotEmpty( answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_DATE_PROGRAMMATION ) )
                                            ? answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_DATE_PROGRAMMATION )
                                                    : StringUtils.EMPTY;
                                    long idTypeAnomalie = PARAMETER_NULL.equals( answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE ) )
                                            ? -1
                                                    : answer.getLong( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE );

                                    request.getSession( ).setAttribute( PARAMETER_WEBSERVICE_CHOSEN_MESSAGE, chosenMessage );

                                    _manageSignalementService.manageStatusWithWorkflow( request, jsonObject, jsonAnswer, id, signalementreference, status, null,
                                            motifRejetAutre, strDateProgrammee, idTypeAnomalie, comment, emailActeur );

                                    String photoSF = null;
                                    if ( answer.containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_PHOTO ) )
                                    {
                                        photoSF = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_PHOTO ).equals( PARAMETER_NULL ) ? null
                                                : answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_PHOTO );
                                    }

                                    if ( ( photoSF != null ) && SignalementRestConstants.JSON_TAG_ANOMALY_DONE.equals( status ) )
                                    {
                                        PhotoDMR photo = new PhotoDMR( );

                                        ImageResource image = new ImageResource( );
                                        String width = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_WIDTH );
                                        String height = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_HEIGHT );

                                        String mimeType = photoSF.substring( photoSF.indexOf( "image" ), photoSF.indexOf( BASE_64 ) );

                                        String [ ] array = photoSF.split( BASE_64 );

                                        byte [ ] photoDecode = java.util.Base64.getDecoder( ).decode( array [1] );

                                        byte [ ] resizeImage = ImageUtil.resizeImage( photoDecode, width, height, 1 );
                                        image.setImage( photoDecode );
                                        mimeType = mimeType.replace( "x-png", "png" );
                                        image.setMimeType( mimeType );
                                        photo.setImage( image );
                                        photo.setImageContent( ImgUtils.checkQuality( image.getImage( ) ) );
                                        photo.setImageThumbnailWithBytes( resizeImage );
                                        photo.setSignalement( signalement );
                                        photo.setVue( SignalementRestConstants.VUE_SERVICE_FAIT );
                                        photo.setDate( new SimpleDateFormat( DateUtils.DATE_FR ).format( Calendar.getInstance( ).getTime( ) ) );

                                        // creation of the image in the db linked to the signalement
                                        _photoService.insert( photo );

                                    }

                                }

                            }
                            else
                            {
                                // NOK le token ne correspond pas
                                jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                                        I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_NO_TOKEN_FOUND, request.getLocale( ) ) );
                                jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
                            }
                    }
                }
                catch( JSONException e )
                {
                    AppLogService.error( e.getMessage( ), e );
                    jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                            I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_DONE_WRONG_STATUS, request.getLocale( ) ) );
                    jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
                }
            }
        return jsonObject.toString( );
    }

    /**
     * Check if the jsonSrc has the correct format.
     *
     * @param jsonSrc
     *            the json
     * @return the boolean result
     */
    public boolean isGoodFormat( JSONObject jsonSrc )
    {
        return jsonSrc.containsKey( SignalementRestConstants.JSON_TAG_ANSWER )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_ID )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_STATUS )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_DATE_REEL_ACTION )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_COMMENT )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_ID_REJET )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER )
                .containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_DATE_PROGRAMMATION )
                && jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE );
    }

    /**
     * Check if the jsonSrc's type signalement is selectable and also active (via type id).
     *
     * @param jsonSrc
     *            the json
     * @return the boolean result
     */
    public boolean isTypeSignalementSelectable( JSONObject jsonSrc )
    {
        String status = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER ).getString( SignalementRestConstants.JSON_TAG_STATUS );
        String idTypeAnomalie = jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER )
                .containsKey( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE )
                ? jsonSrc.getJSONObject( SignalementRestConstants.JSON_TAG_ANSWER )
                        .getString( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE )
                        : null;

        if ( SignalementRestConstants.JSON_TAG_ANOMALY_REQUALIFIED.equals( status ) && StringUtils.isNumeric( idTypeAnomalie ) )
        {
            TypeSignalement selectedTypeSignalement = _typeSignalementService.getByIdTypeSignalement( Integer.parseInt( idTypeAnomalie ) );

            return ( ( selectedTypeSignalement != null ) && selectedTypeSignalement.getActif( )
                    && _typeSignalementService.isTypeSignalementSelectable( selectedTypeSignalement.getId( ) ) );
        }
        else
        {
            return true;
        }
    }

    /**
     * Check the status for a change status request.
     *
     * @param jsonObject
     *            the jsonObject
     * @param answer
     *            the answer
     * @param jsonAnswer
     *            the json answer
     * @param request
     *            the http request
     * @return true if the status is correct
     */
    private boolean controleCoherenceStatus( JSONObject jsonObject, JSONObject answer, JSONObject jsonAnswer, HttpServletRequest request )
    {
        boolean result = true;

        String status = answer.getString( SignalementRestConstants.JSON_TAG_STATUS );

        if ( status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_REJECTED ) )
        {
            // Pour le rejet la raison du rejet est obigatoire
            String raisonDuRejet = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ID_REJET );
            if ( PARAMETER_NULL.equals( raisonDuRejet ) || StringUtils.isBlank( raisonDuRejet ) )
            {

                jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                        I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_REJECTED_WRONG_REJECT_CAUSE_ID, request.getLocale( ) ) );
                jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

                result = false;
            }
        }
        else
            if ( status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_REQUALIFIED ) )
            {
                // pour le status requalifier le type d'anomalie est obligatoire
                String idTypeAnomalie = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_TYPE_ANOMALIE );
                if ( PARAMETER_NULL.equals( idTypeAnomalie ) || StringUtils.isBlank( idTypeAnomalie ) || !StringUtils.isNumeric( idTypeAnomalie ) )
                {

                    jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                            I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_REQUALIFIED_WRONG_TYPE_ANOMALIE, request.getLocale( ) ) );
                    jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

                    result = false;
                }
            }
            else
                if ( status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_PROGRAMMED ) )
                {
                    // Pour le status programmer la date de programmation est obligatoire
                    String strDateProgrammee = answer.getString( SignalementRestConstants.JSON_TAG_INCIDENT_DATE_PROGRAMMATION );

                    try
                    {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "dd/MM/yyyy" );
                        LocalDate dateTime = LocalDate.parse( strDateProgrammee, formatter );
                        if ( dateTime.atStartOfDay( ).toInstant( ZoneOffset.UTC ).isBefore( Instant.now( ).truncatedTo( ChronoUnit.DAYS ) ) )
                        {
                            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR, I18nService
                                    .getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_PROGRAMMED_DATE_BEFORE_TODAY, request.getLocale( ) ) );
                            jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

                            result = false;
                        }
                    }
                    catch( DateTimeParseException e )
                    {
                        AppLogService.error( e.getMessage( ), e );

                        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                                I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_PROGRAMMED_WRONG_DATE_FORMAT, request.getLocale( ) ) );
                        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

                        result = false;
                    }
                }
                else
                    if ( !status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_DONE )
                            && !status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_REQUALIFIED )
                            && !status.equals( SignalementRestConstants.JSON_TAG_ANOMALY_A_REQUALIFIED ) )
                    {
                        // Le status n'est pas service fait ou rejeter ou programmer ou requalifier
                        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR,
                                I18nService.getLocalizedString( SignalementRestConstants.ERROR_MESSAGE_DIFFERENT_STATUS, request.getLocale( ) ) );
                        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

                        result = false;
                    }

        return result;
    }

    /**
     * Stub for plug the "add anomalie" WS.
     *
     * @param jsonSrc
     *            the json
     * @return the result
     */
    public String addAnomalie( JSONObject jsonSrc )
    {
        JSONObject anomalie = jsonSrc.getJSONObject( "anomalie" );
        int id = anomalie.getInt( SignalementRestConstants.JSON_TAG_INCIDENT_ID );

        JSONObject jsonObject = new JSONObject( );

        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_ADD_ANOMALIE );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_INCIDENT_ID, id );

        jsonObject.accumulate( "answer", jsonAnswer );

        return jsonObject.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCategoriesList( JSONObject jsonSrc )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );

        double dCurVersion = jsonSrc.getDouble( SignalementRestConstants.JSON_TAG_CATEGORIES_CURVERSION );

        JSONObject jsonObject = new JSONObject( );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_CATEGORIES_LIST );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        double dVersion = _typeSignalementService.findLastVersionTypeSignalement( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_CATEGORIES_VERSION, dVersion );

        if ( Double.compare( dVersion, dCurVersion ) != 0 )
        {
            if ( !jsonSrc.has( SignalementRestConstants.JSON_TAG_CATEGORIES_CURVERSION_MOBILE_PROD ) )
            {
                IFormatter<TypeSignalement> formatterJson = new CategoriesFormatterJson( );
                jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_CATEGORIES,
                        formatterJson.format( _typeSignalementService.getAllTypeSignalementByIsAgent( false ) ) );
            }
            else
            {
                IFormatter<TypeSignalement> formatterJson = new CategoriesFormatterJson( );
                jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_CATEGORIES, formatterJson.format( _typeSignalementService.getAll( ) ) );
            }

        }
        else
        {
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_CATEGORIES, PARAMETER_NULL );
        }
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );

        return jsonObject.toString( );
    }

    /**
     * Gets the categories list xml.
     *
     * @return signalement type list XML format
     */
    public String getCategoriesListXml( )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );

        StringBuilder strFormatted = new StringBuilder( );
        strFormatted.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE annuaire SYSTEM \"signalement_nomenclature.dtd\"><elementList>" );

        for ( TypeSignalementDTO ts : _typeSignalementService.getTypeSignalementTree( true ) )
        {
            formatXml( strFormatted, ts );
        }

        strFormatted.append( "</elementList>" );

        return strFormatted.toString( );
    }

    /**
     * Gets the categories list json.
     *
     * @return signalement type list Json format
     */
    public String getCategoriesListJson( )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );

        List<TypeSignalementDTO> lstSignalementDTO = _typeSignalementService.getTypeSignalementTree( true );

        return lstSignalementDTOToString( lstSignalementDTO );
    }

    /**
     * Gets the categories list json for source.
     *
     * @param nIdSource
     *            the n id source
     * @return the categories list json for source
     */
    public String getCategoriesListJsonForSource( Integer nIdSource )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );

        List<TypeSignalementDTO> lstSignalementDTO = _typeSignalementService.getTypeSignalementTreeFromSource( nIdSource );

        return lstSignalementDTOToString( lstSignalementDTO );
    }

    /**
     * Gets the anomalie by number.
     *
     * @param number
     *            the number
     * @return the anomalie by number
     */
    public String getAnomalieByNumber( String number, String guid )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        JSONObject jsonAnswer = new JSONObject( );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );

        // Récupération du signalement
        Signalement signalement = _signalementService.getAnomalieByNumber( number );

        // Aucun signalement ne correspond au numéro
        if ( signalement == null )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_GET_SIGNALEMENT_BY_NUMBER_NOT_FOUND );
            error.setErrorMessage( AppPropertiesService.getProperty( SignalementRestConstants.ERROR_GET_SIG_BY_NUMBER_NOT_FOUND ) );

            return formatterJsonError.format( error );
        }

        // Controle sur la date de création
        Integer nbJourMax = Integer.parseInt( DatastoreService.getDataValue( "sitelabels.site_property.mobile.nb.jour.recherche.ano.max", "90" ) );

        if ( DateUtils.isDateMoreThanXDay( signalement.getDateCreation( ), nbJourMax ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_GET_SIGNALEMENT_BY_NUMBER_TOO_OLD );
            error.setErrorMessage( AppPropertiesService.getProperty( SignalementRestConstants.ERROR_GET_SIG_BY_NUMBER_TOO_OLD ) );

            return formatterJsonError.format( error );
        }

        SignalementFormatterJson formatterJson = new SignalementFormatterJson( );

        if ( !StringUtils.isBlank( guid ) )
        {
            formatterJson.setFormatWithGuid( guid );
        }

        List<Signalement> listSignalementRest = new ArrayList<>( );
        listSignalementRest.add( signalement );

        JSONArray jsonArray = new JSONArray( );
        jsonArray.add( JSONSerializer.toJSON( formatterJson.format( signalement ) ) );

        json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENT, jsonArray.toString( ) );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Gets the infos for source.
     *
     * @param nIdSource
     *            the n id source
     * @return the infos for source
     */
    public Source getInfosForSource( Integer nIdSource )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );

        return _typeSignalementService.getSourceById( nIdSource );
    }

    /**
     * Lst signalement DTO to string.
     *
     * @param lstSignalementDTO
     *            the lst signalement DTO
     * @return the string
     */
    private String lstSignalementDTOToString( List<TypeSignalementDTO> lstSignalementDTO )
    {
        String result = "[";
        if ( !lstSignalementDTO.isEmpty( ) )
        {
            StringBuilder buf = new StringBuilder( );
            boolean first = true;
            for ( TypeSignalementDTO signalement : lstSignalementDTO )
            {
                buf.append( formatJson( signalement, first ) );
                first = false;
            }
            result += buf.toString( );
        }
        result += "]";

        return result;
    }

    /**
     * Xml representation of type report.
     *
     * @param strFormatted
     *            XML string
     * @param ts
     *            type report
     */
    private void formatXml( StringBuilder strFormatted, TypeSignalementDTO ts )
    {
        strFormatted.append( "<element id=\"" + ts.getId( ) + "\">" );
        strFormatted.append( "<name>" );
        strFormatted.append( ts.getLibelle( ) );
        strFormatted.append( "</name>" );

        List<TypeSignalementDTO> lts = ts.getListChild( );

        if ( ( lts != null ) && ( !lts.isEmpty( ) ) )
        {
            strFormatted.append( "<elements>" );

            for ( TypeSignalementDTO ts2 : lts )
            {
                formatXml( strFormatted, ts2 );
            }

            strFormatted.append( "</elements>" );
        }
        else
        {
            strFormatted.append( "<to id=\"16\">DPE</to>" );
        }

        strFormatted.append( "</element>" );
    }

    /**
     * Format type report in json.
     *
     * @param ts
     *            type report
     * @param firstElement
     *            true if is a top type
     * @return Json string
     */
    private String formatJson( TypeSignalementDTO ts, boolean firstElement )
    {

        StringBuilder sb = new StringBuilder( );
        if ( !firstElement )
        {
            sb.append( "," );
        }
        sb.append( "{\"id\": " + ts.getId( ) + "," );
        sb.append( "\"libelle\": \"" + ts.getLibelle( ).replace( "\"", "" ) + "\"," );
        sb.append( "\"imageUrl\": \"" + getImageBase64( ts.getImage( ) ) + "\"," );
        sb.append( "\"isAgent\": \"" + ts.getIsAgent( ) + "\"" );
        if ( ts.getTypeSignalementParent( ) != null )
        {
            sb.append( ", \"typeSignalementParent\":{\"id\":" + ts.getTypeSignalementParent( ).getId( ) + "}" );
        }
        if ( !ts.getListChild( ).isEmpty( ) )
        {
            sb.append( ",\"listChild\": [" );
            boolean first = true;
            for ( TypeSignalementDTO child : ts.getListChild( ) )
            {
                sb.append( formatJson( child, first ) );
                first = false;
            }

            sb.append( "]" );
        }

        sb.append( "}" );

        return sb.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String updatePictureIncident( HttpServletRequest request, InputStream requestBodyStream )
    {

        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );

        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );

        String strIdIncident = request.getHeader( SignalementRestConstants.PARAMETERS_HEADER_INCIDENT_ID );
        String strPhotoVue = request.getHeader( SignalementRestConstants.PARAMETERS_HEADER_TYPE );
        Signalement signalement = _signalementService.getSignalement( Long.parseLong( strIdIncident ) );

        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream( );
            IOUtils.copy( requestBodyStream, out );

            if ( out.size( ) == 0 )
            {
                ErrorSignalement error = new ErrorSignalement( );
                error.setErrorCode( SignalementRestConstants.ERROR_IMPOSSIBLE_READ_PICTURE );
                error.setErrorMessage( StringUtils.EMPTY );

                return formatterJson.format( error );
            }

            ImageResource image = new ImageResource( );
            image.setImage( out.toByteArray( ) );
            image.setMimeType( request.getContentType( ) );

            String width = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_WIDTH );
            String height = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_HEIGHT );
            byte [ ] resizeImage = ImageUtil.resizeImage( out.toByteArray( ), width, height, 1 );

            PhotoDMR photoSignalement = new PhotoDMR( );
            photoSignalement.setImage( image );
            photoSignalement.setImageContent( ImgUtils.checkQuality( image.getImage( ) ) );
            photoSignalement.setImageThumbnailWithBytes( resizeImage );
            photoSignalement.setMimeType( request.getContentType( ) );
            photoSignalement.setSignalement( signalement );

            if ( StringUtils.isNotBlank( strPhotoVue ) )
            {
                if ( SignalementRestConstants.PICTURE_FAR.equals( strPhotoVue ) )
                {
                    photoSignalement.setVue( SignalementRestConstants.VUE_ENSEMBLE );
                }
                else
                    if ( SignalementRestConstants.PICTURE_CLOSE.equals( strPhotoVue ) )
                    {
                        photoSignalement.setVue( SignalementRestConstants.VUE_PRES );
                    }
                    else
                        if ( SignalementRestConstants.PICTURE_DONE.equals( strPhotoVue ) )
                        {
                            photoSignalement.setVue( SignalementRestConstants.VUE_SERVICE_FAIT );
                        }
            }

            // date of creation
            SimpleDateFormat sdfDate = new SimpleDateFormat( DateUtils.DATE_FR );
            photoSignalement.setDate( sdfDate.format( Calendar.getInstance( ).getTime( ) ) );

            Optional<PhotoDMR> existPhoto = _photoService.findBySignalementId( signalement.getId( ) ).stream( )
                    .filter( photo -> photo.getVue( ).intValue( ) == photoSignalement.getVue( ).intValue( ) ).findFirst( );

            if ( existPhoto.isPresent( ) )
            {
                PhotoDMR photoDMR = existPhoto.get( );
                _photoService.remove( photoDMR.getId( ) );
            }
            _photoService.insert( photoSignalement );

            out.close( );
        }

        catch( Exception e )
        {

            AppLogService.error( e.getMessage( ), e );

            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_PICTURE );
            error.setErrorMessage( e.getMessage( ) );

            AppLogService.info( "java.awt.headless  to true" );
            System.setProperty( "java.awt.headless", "true" );

            return formatterJson.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Upload picture report.
     *
     * @param request
     *            the http request
     * @param uploadedInputStream
     *            the input stream
     * @param fileDetail
     *            file detail
     * @param strIdIncident
     *            the report
     * @return answer
     */
    public String updatePictureIncidentCordova( HttpServletRequest request, InputStream uploadedInputStream, FormDataContentDisposition fileDetail,
            String strIdIncident )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );

        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );

        String strPhotoVue = SignalementRestConstants.PICTURE_FAR;
        if ( strIdIncident == null )
        {
            strIdIncident = request.getHeader( SignalementRestConstants.PARAMETERS_HEADER_INCIDENT_ID );
            strPhotoVue = request.getHeader( SignalementRestConstants.PARAMETERS_HEADER_TYPE );
        }
        Signalement signalement = _signalementService.getSignalement( Long.parseLong( strIdIncident.trim( ) ) );

        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream( );
            IOUtils.copy( uploadedInputStream, out );

            if ( out.size( ) == 0 )
            {
                ErrorSignalement error = new ErrorSignalement( );
                error.setErrorCode( SignalementRestConstants.ERROR_IMPOSSIBLE_READ_PICTURE );
                error.setErrorMessage( StringUtils.EMPTY );

                return formatterJson.format( error );
            }

            ImageResource image = new ImageResource( );
            image.setImage( out.toByteArray( ) );
            image.setMimeType( fileDetail.getType( ) );

            String width = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_WIDTH );
            String height = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_HEIGHT );
            byte [ ] resizeImage = ImageUtil.resizeImage( out.toByteArray( ), width, height, 1 );

            PhotoDMR photoSignalement = new PhotoDMR( );
            photoSignalement.setImage( image );
            photoSignalement.setImageThumbnailWithBytes( resizeImage );
            photoSignalement.setImageContent( ImgUtils.checkQuality( image.getImage( ) ) );
            photoSignalement.setMimeType( request.getContentType( ) );
            photoSignalement.setSignalement( signalement );

            if ( StringUtils.isNotBlank( strPhotoVue ) )
            {
                if ( SignalementRestConstants.PICTURE_FAR.equals( strPhotoVue ) )
                {
                    photoSignalement.setVue( SignalementRestConstants.VUE_ENSEMBLE );
                }
                else
                    if ( SignalementRestConstants.PICTURE_CLOSE.equals( strPhotoVue ) )
                    {
                        photoSignalement.setVue( SignalementRestConstants.VUE_PRES );
                    }
            }

            // date of creation
            SimpleDateFormat sdfDate = new SimpleDateFormat( DateUtils.DATE_FR );
            photoSignalement.setDate( sdfDate.format( Calendar.getInstance( ).getTime( ) ) );

            _photoService.insert( photoSignalement );
            out.close( );
        }

        catch( Exception e )
        {
            AppLogService.error( e.getMessage( ), e );

            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_PICTURE );
            error.setErrorMessage( e.getMessage( ) );

            return formatterJson.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String updatePictureIncidentBlackberry( InputStream source )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );

        PhotoDMR photoSignalement = new PhotoDMR( );
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream( );
            IOUtils.copy( source, out );

            ImageResource image = new ImageResource( );
            image.setImage( out.toByteArray( ) );

            String width = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_WIDTH );
            String height = AppPropertiesService.getProperty( SignalementConstants.IMAGE_THUMBNAIL_RESIZE_HEIGHT );
            byte [ ] resizeImage = ImageUtil.resizeImage( out.toByteArray( ), width, height, 1 );

            photoSignalement.setImage( image );
            photoSignalement.setImageContent( ImgUtils.checkQuality( image.getImage( ) ) );
            photoSignalement.setImageThumbnailWithBytes( resizeImage );
            photoSignalement.setImageContent( out.toByteArray( ) );

            // date of creation
            SimpleDateFormat sdfDate = new SimpleDateFormat( DateUtils.DATE_FR );
            photoSignalement.setDate( sdfDate.format( Calendar.getInstance( ).getTime( ) ) );

            _photoService.insert( photoSignalement );
            out.close( );
        }
        catch( Exception e )
        {
            AppLogService.error( e.getMessage( ), e );
        }

        return "{\"Id\":" + photoSignalement.getId( ) + ",\"photo\":\"\"}";
    }

    /**
     * Do save incident for black berry.
     *
     * @param signalement
     *            the signalement
     * @return the string
     * @deprecated
     */
    @Override
    @Deprecated
    public String doSaveIncidentForBlackBerry( Signalement signalement )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        _adresseService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_ADRESSE_SERVICE_BEAN );
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );
        _signaleurService = SpringContextService.getBean( "signaleurService" );
        _signalementWorkflowService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_WORKFLOW_BEAN );

        // Arrondissement
        Adresse adresse = signalement.getAdresses( ).get( 0 );
        Arrondissement arrondissementByGeom = _adresseService.getArrondissementByGeom( adresse.getLng( ), adresse.getLat( ) );
        signalement.setArrondissement( arrondissementByGeom );

        // Secteur
        Sector secteur = _adresseService.getSecteurByGeomAndTypeSignalement( adresse.getLng( ), adresse.getLat( ), signalement.getTypeSignalement( ).getId( ) );
        signalement.setSecteur( secteur );

        // date of creation
        SimpleDateFormat sdfDate = new SimpleDateFormat( DateUtils.DATE_FR );
        String strCurrentDate = sdfDate.format( Calendar.getInstance( ).getTime( ) );
        signalement.setDateCreation( strCurrentDate );

        // récupération de l'année et la lettre correspondant au mois
        Date dateDay = DateUtils.getDate( signalement.getDateCreation( ), false );
        int moisSignalement = DateUtils.getMoisInt( dateDay );
        String strAnnee = DateUtils.getAnnee( Calendar.getInstance( ).getTime( ) );
        signalement.setAnnee( Integer.parseInt( strAnnee ) );
        signalement.setMois( _signalementService.getLetterByMonth( moisSignalement ) );

        // Préfixe signalement
        signalement.setPrefix( SignalementRestConstants.SIGNALEMENT_PREFIX_TELESERVICE );

        // ajou d'un signalement en base et récupération de son id
        Long nId = _signalementService.insert( signalement );
        signalement.setId( nId );

        adresse.setSignalement( signalement );
        adresse.setId( _adresseService.insert( adresse ) );

        // initialisation d'une photo à vide, sa mise à jour se fera dans un nouvel appel au service rest
        PhotoDMR photoSignalement = new PhotoDMR( );
        photoSignalement.setImage( null );
        photoSignalement.setSignalement( signalement );
        _photoService.insert( photoSignalement );

        // ajout d'un signaleur
        Signaleur signaleur = signalement.getSignaleurs( ).get( 0 );
        signaleur.setSignalement( signalement );
        _signaleurService.insert( signaleur );

        // ajout du workflow
        // set the state of the signalement with the workflow
        WorkflowService workflowService = WorkflowService.getInstance( );

        if ( workflowService.isAvailable( ) )
        {
            // récupération de l'identifiant du workflow
            Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

            if ( workflowId != null )
            {
                // création de l'état initial et exécution des tâches automatiques
                workflowService.executeActionAutomatic( signalement.getId( ).intValue( ), Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null );
            }
            else
            {
                AppLogService.error( "Signalement : No workflow selected" );
            }
        }
        else
        {
            AppLogService.error( "Signalement : Workflow not available" );
        }

        StringBuilder sbJson = new StringBuilder( );
        sbJson.append( "{\"Id\":" );
        sbJson.append( signalement.getId( ) );
        sbJson.append( ",\"Priorite\":\"\",\"Type\":\"\",\"typeId\":\"\",\"\":\"\",\"Adresse\":\"\",\"Mail\":\"\",\"Com\":\"\",\"photo\":\"\"}" );

        return sbJson.toString( );
    }

    /**
     * Hack to get the picture and re give it when the whole signalement arrive.
     *
     * @param id
     *            the id picture
     *
     * @return picture
     */
    public PhotoDMR getPictureForBlackBerry( Long id )
    {
        return _photoService.load( id );
    }

    /**
     * POJO for report.
     *
     * @param listeSignalements
     *            list of report.
     * @return pojo
     */
    private SignalementsPOJO buildPOJO( List<Signalement> listeSignalements )
    {
        List<SignalementPOJO> signalements = new ArrayList<>( );

        for ( Signalement signalement : listeSignalements )
        {
            SignalementPOJO e = new SignalementPOJO( );
            e.setId( signalement.getId( ) );
            signalements.add( e );
        }

        SignalementsPOJO ret = new SignalementsPOJO( );
        ret.setSignalements( signalements );

        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SignalementsPOJO signalementAArchiverRejete( )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        List<Signalement> listeSignalements = _signalementService.getSignalementByStatusId( 11 );

        return buildPOJO( listeSignalements );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SignalementsPOJO signalementAArchiverServiceFait( )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        List<Signalement> listeSignalements = _signalementService.getSignalementByStatusId( 10 );

        return buildPOJO( listeSignalements );
    }

    /**
     * Methode to sort the signalement list.
     *
     * @param lat1
     *            lat coordinate
     * @param lng1
     *            lng coordinate
     *
     * @param listSignalement
     *            list of report
     * @return list sorted
     */
    private List<Signalement> getSignalementListSorted( double lat1, double lng1, List<Signalement> listSignalement )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        // tri des signalements pas rapport à leur distance la plus prochaine de l'utilisateur
        List<SignalementGeoLoc> listSignalementGeoLoc = new ArrayList<>( );
        for ( Signalement signalement : listSignalement )
        {
            Adresse adresse = signalement.getAdresses( ).get( 0 );
            if ( adresse != null )
            {
                Integer distance = _signalementService.getDistanceBetweenSignalement( lat1, lng1, adresse.getLat( ), adresse.getLng( ) );
                SignalementGeoLoc signalementGeoLoc = new SignalementGeoLoc( );
                signalementGeoLoc.setSignalement( signalement );
                signalementGeoLoc.setDistance( distance );
                listSignalementGeoLoc.add( signalementGeoLoc );
            }
        }
        Collections.sort( listSignalementGeoLoc );

        List<Signalement> listSignalementSorted = new ArrayList<>( );
        for ( SignalementGeoLoc signalementGeoLoc : listSignalementGeoLoc )
        {
            listSignalementSorted.add( signalementGeoLoc.getSignalement( ) );
        }
        return listSignalementSorted;
    }

    /**
     * Updates the congratulation count of an anomalie.
     *
     * @param jsonSrc
     *            json src.
     * @return the answer
     */
    public String congratulateAnomalie( JSONObject jsonSrc )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        if ( StringUtils.isBlank( strIncidentId ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        _signalementService.incrementFelicitationsByIdSignalement( Integer.valueOf( strIncidentId ) );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_CONGRATULATE_ANOMALIE );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * service to past report to done status.
     *
     * @param signalement
     *            the report
     * @param request
     *            the http request
     * @return true if is validate
     */
    public boolean validateServiceFaitSignalement( Signalement signalement, HttpServletRequest request )
    {

        boolean response = false;

        JSONObject jObject = new JSONObject( );
        jObject.accumulate( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID, signalement.getId( ).toString( ) );
        jObject.accumulate( SignalementRestConstants.JSON_TAG_GUID, signalement.getSignaleurs( ).get( 0 ).getGuid( ) );
        jObject.accumulate( SignalementRestConstants.JSON_TAG_EMAIL, signalement.getSignaleurs( ).get( 0 ).getMail( ) );

        String resolved = setIncidentResolved( jObject, request );

        if ( !resolved.isEmpty( ) && !resolved.contains( "error" ) )
        {
            response = true;
        }

        return response;
    }

    /**
     * Sets the incident as resolved.
     *
     * @param jsonSrc
     *            json source
     * @param request
     *            the http request
     * @return the answer
     */
    public String setIncidentResolved( JSONObject jsonSrc, HttpServletRequest request )
    {

        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );
        if ( StringUtils.isBlank( strIncidentId ) )
        {
            AppLogService.error( "Error id incident empty or null !! " );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }
        String guid = StringUtils.EMPTY;
        try
        {
            guid = jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        String strUDID = StringUtils.EMPTY;
        try
        {
            strUDID = jsonSrc.getString( SignalementRestConstants.JSON_TAG_UDID );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        String email = StringUtils.EMPTY;
        try
        {
            email = jsonSrc.getString( SignalementRestConstants.JSON_TAG_EMAIL );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        if ( StringUtils.isBlank( guid ) && StringUtils.isBlank( strUDID ) )
        {
            AppLogService.error( "Error guid and strUDIDempty or null !! " );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        Signalement signalement = _signalementService.getSignalement( Integer.parseInt( strIncidentId ) );

        if ( signalement == null )
        {
            AppLogService.error( "signalement not found !! " );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        List<Signaleur> signaleurs = signalement.getSignaleurs( );

        boolean signaleurFound = false;

        for ( Signaleur signaleur : signaleurs )
        {
            if ( ( StringUtils.isNotBlank( guid ) && guid.equals( signaleur.getGuid( ) ) )
                    || ( StringUtils.isNotBlank( email ) && email.equals( signaleur.getMail( ) ) ) )
            {
                signaleurFound = true;
            }
        }

        // Si le signaleur n'est pas trouvé et que l'email ne finit pas par @paris.fr, remonte une erreur
        if ( !signaleurFound && !StringUtils.endsWithAny( email.toLowerCase( ), getEmailDomainAccept( ) ) )
        {
            AppLogService.error( "signaleur not found and bad domain email !! " );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_OWNER );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        String comment = StringUtils.EMPTY;
        try
        {
            comment = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_COMMENT );
        }
        catch( Exception e )
        {
            AppLogService.error( e );
        }

        // recuperation numero message service fait
        if ( jsonSrc.containsKey( SignalementRestConstants.JSON_TAG_NUMERO_MESSAGE_SERVICE_FAIT )
                && jsonSrc.containsKey( SignalementRestConstants.JSON_TAG_TYPE_MESSAGE_SERVICE_FAIT ) )
        {
            request.getSession( ).setAttribute( PARAMETER_WEBSERVICE_CHOSEN_MESSAGE,
                    jsonSrc.getInt( SignalementRestConstants.JSON_TAG_NUMERO_MESSAGE_SERVICE_FAIT ) );
            request.getSession( ).setAttribute( PARAMETER_WEBSERVICE_IS_MESSAGE_TYPO,
                    !jsonSrc.getBoolean( SignalementRestConstants.JSON_TAG_TYPE_MESSAGE_SERVICE_FAIT ) );
        }

        // récupération de l'identifiant du workflow
        Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

        int idIncident = Integer.parseInt( strIncidentId );

        WorkflowService workflowService = WorkflowService.getInstance( );

        int stateid = workflowService.getState( idIncident, Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null ).getId( );

        int idStatutServiceFait = AppPropertiesService.getPropertyInt( SignalementRestConstants.PROPERTY_ID_STATE_SERVICE_FAIT, -1 );

        int idActionServiceFait = _signalementWorkflowService.selectIdActionByStates( stateid, idStatutServiceFait );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_INCIDENT_RESOLVED );

        JSONObject json = new JSONObject( );

        if ( idActionServiceFait > -1 )
        {
            request.getSession( ).setAttribute( PARAMETER_WEBSERVICE_COMMENT_VALUE, comment );
            workflowService.doProcessAction( idIncident, Signalement.WORKFLOW_RESOURCE_TYPE, idActionServiceFait, null, request, request.getLocale( ), true );

            if ( StringUtils.isNotEmpty( email ) )
            {
                ResourceHistory resourceHistory = _signalementWorkflowService.getLastHistoryResource( signalement.getId( ).intValue( ),
                        Signalement.WORKFLOW_RESOURCE_TYPE, workflowId );
                _signalementWorkflowService.setUserAccessCodeHistoryResource( email, resourceHistory.getId( ) );
            }
        }
        else
        {
            AppLogService.error( "idActionServiceFait not found !! " );

            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ERROR_ERROR, SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, -1 );
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );
            return jsonAnswer.toString( );
        }

        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );

    }

    /**
     * Method to get user information from identity store based on guid param and attribute name param.
     *
     * @param guid
     * @param attributeName
     * @return the value of attribute
     */
    public String getIdentityStoreAttributeValue( String guid, String attributeName )
    {
        IdentityService identityStore = SpringContextService.getBean( "identitystoremyluteceprovider.identitystore.identityService" );
        IdentityDto identityDTO = null;
        try
        {
            identityDTO = identityStore.getIdentityByConnectionId( guid,
                    AppPropertiesService.getProperty( "identitystoremyluteceprovider.identityStoreApplicationCode" ) );
        }
        catch( IdentityNotFoundException | IdentityStoreException e )
        {
            AppLogService.error( e );
            return StringUtils.EMPTY;
        }

        return identityDTO.getAttributes( ).get( attributeName ) != null ? identityDTO.getAttributes( ).get( attributeName ).getValue( ) : "";
    }

    /**
     * Adds a follower to a report.
     *
     * @param jsonSrc
     *            the json src
     * @return answer
     */
    public String addFollower( JSONObject jsonSrc )
    {
        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );
        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        if ( StringUtils.isBlank( strIncidentId ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }
        String guid = jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID );
        if ( StringUtils.isBlank( guid ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        String strUDID = jsonSrc.getString( SignalementRestConstants.JSON_TAG_UDID );
        if ( StringUtils.isBlank( strUDID ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        String email = jsonSrc.getString( SignalementRestConstants.JSON_TAG_EMAIL );
        if ( StringUtils.isBlank( email ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_EMAIL );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        String device = jsonSrc.getString( SignalementRestConstants.JSON_TAG_DEVICE );
        if ( StringUtils.isBlank( device ) || ( !device.equals( SignalementRestConstants.SIGNALEMENT_PREFIX_IOS )
                && !device.equals( SignalementRestConstants.SIGNALEMENT_PREFIX_ANDROID ) ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_DEVICE );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        String userToken = jsonSrc.getString( SignalementRestConstants.JSON_TAG_USER_TOKEN );
        if ( StringUtils.isBlank( userToken ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_TOKEN );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        int returnCode = 0;

        // Appel du service pour suivi de l'anomalie
        try
        {
            _signalementService.addFollower( Long.parseLong( strIncidentId ), guid, strUDID, email, device, userToken, true );
        }
        catch( AlreadyFollowedException e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_ALREADY_FOLLOWED );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJsonError.format( error );
        }
        catch( InvalidStateActionException e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_INVALID_STATE_ACTION );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJsonError.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_FOLLOW );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, returnCode );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Removes a follower from an incident.
     *
     * @param jsonSrc
     *            jsonSrc
     * @return answer
     */
    public String removeFollower( JSONObject jsonSrc )
    {
        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );
        String strIncidentId = jsonSrc.getString( SignalementRestConstants.JSON_TAG_LOG_INCIDENT_ID );
        if ( StringUtils.isBlank( strIncidentId ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }
        String guid = jsonSrc.getString( SignalementRestConstants.JSON_TAG_GUID );
        if ( StringUtils.isBlank( guid ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_ID );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        try
        {
            _signalementService.removeFollower( Long.parseLong( strIncidentId ), guid );
        }
        catch( NonExistentFollowItem e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_NON_EXISTENT_FOLLOW_ITEM );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJsonError.format( error );
        }

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_UNFOLLOW );

        JSONObject json = new JSONObject( );
        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Gets all signalements which are followed by this user.
     *
     * @param jsonSrc
     *            the jspo src
     * @return list of report
     */
    public String getIncidentsByUser( JSONObject jsonSrc )
    {

        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        String email = jsonSrc.getString( SignalementRestConstants.JSON_TAG_EMAIL );
        if ( StringUtils.isBlank( email ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_EMAIL );
            error.setErrorMessage( StringUtils.EMPTY );
            return formatterJsonError.format( error );
        }

        boolean filterOnResolved = SignalementRestConstants.MOBILE_STATE_RESOLVED
                .equals( jsonSrc.getString( SignalementRestConstants.JSON_TAG_FILTER_INCIDENT_STATUS ) );

        List<Signalement> listSignalement = _signalementService.getSignalementsByEmail( email, filterOnResolved );
        List<SignalementRestDTO> listSignalementRestDTO = new ArrayList<>( );
        for ( Signalement signalement : listSignalement )
        {
            SignalementRestDTO signalementRestDTO = new SignalementRestDTO( );
            signalementRestDTO.setSignalement( signalement );
            signalementRestDTO.setFollowedByUser( true );
            listSignalementRestDTO.add( signalementRestDTO );
        }

        IFormatter<SignalementRestDTO> formatterJson = new SignalementRestDTOFormatterJson( );

        JSONArray jsonArray = (JSONArray) JSONSerializer.toJSON( formatterJson.format( listSignalementRestDTO ) );

        JSONObject json = new JSONObject( );

        json.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        json.accumulate( SignalementRestConstants.JSON_TAG_INCIDENTS, jsonArray.toString( ) );

        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_GET_INCIDENTS_BY_USER );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, json );

        return jsonAnswer.toString( );
    }

    /**
     * Triggers the workflow process for a given signalement. Only for the initial state.
     *
     * @param jsonSrc
     *            the json src
     * @return answer
     */
    public String processWorkflow( JSONObject jsonSrc )
    {
        IFormatter<ErrorSignalement> formatterJsonError = new ErrorSignalementFormatterJson( );

        String idSignalement = jsonSrc.getString( SignalementRestConstants.JSON_TAG_INCIDENT_ID );

        if ( StringUtils.isBlank( idSignalement ) )
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_EMPTY_INCIDENT_ID_BIS );
            error.setErrorMessage( StringUtils.EMPTY );

            return formatterJsonError.format( error );
        }

        Signalement signalement = new Signalement( );
        signalement.setId( Long.parseLong( idSignalement ) );

        // ajout du workflow
        // set the state of the signalement with the workflow
        WorkflowService workflowService = WorkflowService.getInstance( );

        if ( workflowService.isAvailable( ) )
        {
            // récupération de l'identifiant du workflow
            Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );

            if ( workflowId != null )
            {
                State state = workflowService.getState( signalement.getId( ).intValue( ), Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null );

                int etatInitialId = AppPropertiesService.getPropertyInt( ID_STATE_ETAT_INITIAL, -1 );

                // A exécuter uniquement lorsque état initial
                if ( ( state == null ) || ( state.getId( ) == etatInitialId ) )
                {
                    // création de l'état initial et exécution des tâches automatiques
                    workflowService.executeActionAutomatic( signalement.getId( ).intValue( ), Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null );
                }
                else
                {
                    ErrorSignalement error = new ErrorSignalement( );
                    error.setErrorCode( SignalementRestConstants.ERROR_INVALID_STATE_ACTION );
                    error.setErrorMessage( "Le statut de l'anomalie ne permet pas cette action" );
                    return formatterJsonError.format( error );
                }
            }
            else
            {
                ErrorSignalement error = new ErrorSignalement( );
                error.setErrorCode( SignalementRestConstants.ERROR_NO_WORKFLOW_SELECTED );
                error.setErrorMessage( "No workflow selected" );
                return formatterJsonError.format( error );
            }
        }
        else
        {
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_NO_WORKFLOW_AVAILABLE );
            error.setErrorMessage( "Workflow not available" );
            return formatterJsonError.format( error );
        }

        Signaleur signaleur = _signaleurService.loadByIdSignalement( Long.parseLong( idSignalement ) );

        // Ajout du suivi si guid renseigné
        try
        {
            if ( ( null != signaleur ) && StringUtils.isNotBlank( signaleur.getGuid( ) ) )
            {
                _signalementService.addFollower( Long.parseLong( idSignalement ), signaleur.getGuid( ), signaleur.getIdTelephone( ), signaleur.getMail( ),
                        signalement.getPrefix( ), null, false );
            }
        }
        catch( AlreadyFollowedException e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_ALREADY_FOLLOWED );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJsonError.format( error );
        }
        catch( InvalidStateActionException e )
        {
            AppLogService.error( e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_INVALID_STATE_ACTION );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJsonError.format( error );
        }

        JSONObject jsonObject = new JSONObject( );
        JSONObject jsonAnswer = new JSONObject( );
        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonAnswer );
        jsonObject.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_PROCESS_WORKFLOW );

        AppLogService.info( "processWorkflow, OK " + signalement.getId( ) );

        return jsonObject.toString( );
    }

    /**
     * Check version.
     *
     * @return the string
     */
    public String checkVersion( )
    {
        JSONObject jsonAnswer = new JSONObject( );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_REQUEST, SignalementRestConstants.REQUEST_TYPE_CHECK_VERSION );

        JSONObject jsonObject = new JSONObject( );
        jsonObject.accumulate( "androidVersionStore", DatastoreService.getDataValue( "sitelabels.site_property.android.version.store", null ) );
        jsonObject.accumulate( "androidMajObligatoire", DatastoreService.getDataValue( "sitelabels.site_property.android.maj.obligatoire", null ) );
        jsonObject.accumulate( "derniereVersionObligatoire",
                DatastoreService.getDataValue( "sitelabels.site_property.android.derniere.version.obligatoire", null ) );
        jsonObject.accumulate( "iosMajObligatoire", DatastoreService.getDataValue( "sitelabels.site_property.ios.maj.obligatoire", null ) );
        jsonObject.accumulate( "iosDerniereVersionObligatoire",
                DatastoreService.getDataValue( "sitelabels.site_property.ios.derniere.version.obligatoire", null ) );

        jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonObject );

        return jsonAnswer.toString( );

    }

    /**
     * All report type.
     *
     * @param typeSignalementId
     *            the id of type report
     * @param listeTypeSignalement
     *            list type of report
     * @return the all sous type signalement cascade
     */
    public void getAllSousTypeSignalementCascade( Integer typeSignalementId, List<TypeSignalement> listeTypeSignalement )
    {
        _typeSignalementService.getAllSousTypeSignalementCascade( typeSignalementId, listeTypeSignalement );
    }

    /**
     * Get report by id.
     *
     * @param lIdSignalement
     *            the report id
     * @return the report
     */
    public Signalement getSignalementByID( Long lIdSignalement )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        return _signalementService.getSignalement( lIdSignalement );
    }

    /**
     * Get report pictures.
     *
     * @param lIdSignalement
     *            the report id
     * @return list of picture
     */
    public List<Photo> getPhotosBySignalementId( Long lIdSignalement )
    {
        _photoService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_PHOTO_SERVICE_BEAN );
        List<PhotoDMR> photosDMR = _photoService.findWithFullPhotoBySignalementId( lIdSignalement );
        List<Photo> photos = new ArrayList<>( );
        for ( PhotoDMR photoDMR : photosDMR )
        {
            Photo photo = new Photo( photoDMR.getId( ), null, photoDMR.getImageThumbnail( ), photoDMR.getDate( ), photoDMR.getVue( ) );
            photo.setImageThumbnailUrl( getImageBase64( photo.getImageThumbnail( ) ) );
            photos.add( photo );
        }

        return photos;
    }

    /**
     * Get report address.
     *
     * @param lIdSignalement
     *            the id report
     * @return report address
     */
    public String getAdresseBySignalementId( Long lIdSignalement )
    {
        _adresseService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_ADRESSE_SERVICE_BEAN );
        return _adresseService.loadByIdSignalement( lIdSignalement ).getAdresse( );
    }

    /**
     * Get Type of report.
     *
     * @param nIdSignalement
     *            the id report
     * @return type of report
     */
    public TypeSignalement getTypeSignalement( Integer nIdSignalement )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );
        return _typeSignalementService.getTypeSignalement( nIdSignalement );
    }

    /**
     * Find type by ID.
     *
     * @param nIdSignalement
     *            the id report
     * @return type of report
     */
    public TypeSignalement findByIdTypeSignalement( Integer nIdSignalement )
    {
        _typeSignalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_TYPE_SIGNALEMENT_BEAN );
        return _typeSignalementService.findByIdTypeSignalement( nIdSignalement );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Address> getAddressItem( String address )
    {
        _addressSuggestPOIService = SpringContextService.getBean( "addressSuggestPOIService" );
        return _addressSuggestPOIService.getAddressItem( address );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Double [ ] getGeomFromLambertToWgs84( Double dLatLambert, Double dLngLambert )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        return _signalementService.getGeomFromLambertToWgs84( dLatLambert, dLngLambert );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Double [ ] getGeomFromLambert93ToWgs84( Double dLatLambert, Double dLngLambert )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        return _signalementService.getGeomFromLambert93ToWgs84( dLatLambert, dLngLambert );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DossierSignalementDTO> findAllSignalementInPerimeterWithDTO( Double lat, Double lng, Integer radius )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        return _signalementService.findAllSignalementInPerimeterWithDTO( lat, lng, radius );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getDistanceBetweenSignalement( double lat1, double lng1, double lat2, double lng2 )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );
        return _signalementService.getDistanceBetweenSignalement( lat1, lng1, lat2, lng2 );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSignalementFollowableAndisSignalementFollowedByUser( int nIdSignalement, String userGuid )
    {
        _signalementService = SpringContextService.getBean( SignalementRestConstants.PARAMETER_SIGNALEMENT_SERVICE_BEAN );

        boolean isSignalementFollowedByUser = false;
        if ( userGuid != null )
        {
            isSignalementFollowedByUser = _signalementService.isSignalementFollowedByUser( nIdSignalement, userGuid );
        }

        return !_signalementService.isSignalementFollowable( nIdSignalement ) || isSignalementFollowedByUser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Priorite> getAllPriorite( )
    {
        return _prioriteService.getAllPriorite( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Priorite loadPrioriteById( long lId )
    {
        return _prioriteService.load( lId );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Arrondissement getArrondissementByGeom( double lng, double lat )
    {
        return _arrondissementService.getArrondissementByGeom( lng, lat );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFollower( Long signalementId, String guid, String strUDID, String email, String device, String userToken, boolean createUser )
    {
        _signalementService.addFollower( signalementId, guid, strUDID, email, device, userToken, createUser );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JSONObject sauvegarderSignalement( Signalement demandeSignalement, String userName, String userMail )
    {
        SignalementOutputPrcessor signalementProcessor = new SignalementOutputPrcessor( );
        return signalementProcessor.sauvegarderSignalementFromWS( demandeSignalement, userName, userMail );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Action> getListActionsByIdSignalementAndUser( int nIdSignalement, AdminUser user )
    {
        return _signalementService.getListActionsByIdSignalementAndUser( nIdSignalement, _signalementWorkflowService.getSignalementWorkflowId( ), user );
    }

    /**
     * Gets the image base 64.
     *
     * @param image
     *            the image
     * @return the image base 64
     */
    public String getImageBase64( ImageResource image )
    {
        String dataImg = "";
        if ( ( image != null ) && ( image.getImage( ) != null ) )
        {
            Base64 codec = new Base64( );
            String data = new String( codec.encode( image.getImage( ) ) );
            String mimeType = ( image.getMimeType( ) == null ) ? "data:image/jpg;base64," : ( "data:" + image.getMimeType( ) + BASE_64 );
            dataImg = mimeType + data;
        }

        return dataImg;
    }

    /**
     * Valid email domain.
     *
     * @return array of valid domain
     */
    private String [ ] getEmailDomainAccept( )
    {
        String property = AppPropertiesService.getProperty( SignalementRestConstants.PROPERTY_LIST_DOMAIN_EMAIL );
        String [ ] listDomainArr = null;
        if ( StringUtils.isNotBlank( property ) )
        {

            if ( property.contains( "," ) )
            {
                listDomainArr = property.split( "," );
            }
            else
            {
                listDomainArr = new String [ 1];
                listDomainArr [0] = property;
            }

            // Fix pb de case
            for ( int i = 0; i < listDomainArr.length; ++i )
            {
                listDomainArr [i] = listDomainArr [i].toLowerCase( );
            }
        }
        return listDomainArr;
    }

    /**
     * Find a report with token.
     *
     * @param token
     *            report token
     * @return a report
     */
    public Signalement getSignalementByToken( String token )
    {
        return _signalementService.getSignalementByToken( token );
    }

    /**
     * Find history for a report.
     *
     * @param idSignalement
     *            id report.
     * @param request
     *            the http request
     * @return history in json format
     */
    public JSONObject getHistorySignalement( Integer idSignalement, HttpServletRequest request )
    {
        return _signalementService.getHistorySignalement( idSignalement, request );
    }

    /**
     * Gets the message service fait generic.
     *
     * @param idSignalement
     *            the id signalement
     * @return the message service fait generic
     */
    public JSONArray getMessageServiceFaitGeneric( Long idSignalement )
    {

        WorkflowService workflowService = WorkflowService.getInstance( );
        // récupération de l'identifiant du workflow
        Integer workflowId = _signalementWorkflowService.getSignalementWorkflowId( );
        // current state signalement
        int stateSignalement = workflowService.getState( idSignalement.intValue( ), Signalement.WORKFLOW_RESOURCE_TYPE, workflowId, null ).getId( );

        ActionFilter filter = new ActionFilter( );
        filter.setIdStateBefore( stateSignalement );
        filter.setIdStateAfter( AppPropertiesService.getPropertyInt( SignalementRestConstants.PROPERTY_ID_STATE_SERVICE_FAIT, -1 ) );
        List<Action> actions = _actionService.getListActionByFilter( filter );
        List<NotificationSignalementUserMultiContents> lstMessages = new ArrayList<>( );
        for ( Action action : actions )
        {
            List<ITask> tasks = _taskService.getListTaskByIdAction( action.getId( ), Locale.FRENCH );
            lstMessages.addAll( _signalementWorkflowService.getMessagesServiceFait( tasks ) );
        }

        JSONArray messages = new JSONArray( );
        for ( NotificationSignalementUserMultiContents message : lstMessages )
        {
            JSONObject jsonmessage = new JSONObject( );
            jsonmessage.accumulate( SignalementRestConstants.JSON_TAG_NUMERO_MESSAGE, message.getIdMessage( ) );
            jsonmessage.accumulate( SignalementRestConstants.JSON_TAG_MESSAGE, message.getTitle( ) );

            messages.add( jsonmessage );
        }

        return messages;

    }

    /**
     * Gets the message service fait by typologie.
     *
     * @param idTypeSignalement
     *            the id type signalement
     * @return the message service fait typologie
     */
    public JSONArray getMessageServiceFaitTypologie( Integer idTypeSignalement )
    {
        List<MessageTypologie> listMessageTypologie = _messageTypologieService.loadAllMessageActifByIdType( idTypeSignalement );

        JSONArray messages = new JSONArray( );
        for ( MessageTypologie message : listMessageTypologie )
        {
            JSONObject jsonmessage = new JSONObject( );
            jsonmessage.accumulate( SignalementRestConstants.JSON_TAG_NUMERO_MESSAGE, message.getId( ) );
            jsonmessage.accumulate( SignalementRestConstants.JSON_TAG_MESSAGE, message.getTypeMessage( ) );

            messages.add( jsonmessage );
        }

        return messages;
    }

    /**
     * Select message for service done.
     *
     * @return all the message
     */
    public JSONObject selectMessageServiceFaitPresta( )
    {
        JSONObject jsonAnswer = new JSONObject( );
        JSONArray jsonArray = new JSONArray( );

        Map<Integer, List<NotificationSignalementUserMultiContents>> listMessages = _signalementWorkflowService.selectMessageServiceFaitPresta( null );

        // Création des blocs message
        for ( Map.Entry<Integer, List<NotificationSignalementUserMultiContents>> entry : listMessages.entrySet( ) )
        {
            JSONObject statesMessages = new JSONObject( );

            JSONArray messages = new JSONArray( );

            ITask task = _taskService.findByPrimaryKey( entry.getKey( ), Locale.FRENCH );
            Action action = _actionService.findByPrimaryKey( task.getAction( ).getId( ) );
            State state = _stateService.findByPrimaryKey( action.getStateBefore( ).getId( ) );

            for ( NotificationSignalementUserMultiContents mess : entry.getValue( ) )
            {
                JSONObject message = new JSONObject( );
                message.accumulate( SignalementRestConstants.JSON_TAG_NUMERO_MESSAGE, mess.getIdMessage( ) );
                message.accumulate( SignalementRestConstants.JSON_TAG_MESSAGE, mess.getTitle( ) );

                messages.add( message );
            }

            statesMessages.accumulate( "statut_anomalie", state.getName( ) );
            statesMessages.accumulate( "messages", messages );

            jsonArray.add( statesMessages );
        }
        jsonAnswer.accumulate( "messages_sf", jsonArray );

        return jsonAnswer;
    }

    /**
     * Gets the raison rejet.
     *
     * @return the raison rejet
     */
    public List<ObservationRejet> getRaisonRejet( )
    {
        return _observationRejetService.getAllObservationRejetActif( );
    }

    /**
     * Method to get user information from identity store based on guid param request.
     *
     * @param strRequest
     *            the json stream sended by request
     * @return the answer to request
     */
    public String processIdentityStore( String strRequest )
    {
        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );
        try
        {
            JSONObject json = (JSONObject) JSONSerializer.toJSON( strRequest );
            String guid = json.getString( SignalementRestConstants.JSON_TAG_GUID );
            String name = getIdentityStoreAttributeValue( guid, "family_name" );
            if ( null == name )
            {
                name = StringUtils.EMPTY;
            }
            String firstname = getIdentityStoreAttributeValue( guid, "first_name" );
            String mail = getIdentityStoreAttributeValue( guid, "login" );
            JSONObject userJson = new JSONObject( );
            userJson.accumulate( SignalementRestConstants.JSON_TAG_NAME, name );
            userJson.accumulate( SignalementRestConstants.JSON_TAG_FIRSTNAME, firstname );
            userJson.accumulate( SignalementRestConstants.JSON_TAG_MAIL, mail );
            userJson.accumulate( SignalementRestConstants.JSON_TAG_IS_AGENT, StringUtils.endsWithAny( mail.toLowerCase( ), getEmailDomainAccept( ) ) );

            JSONObject jsonAnswer = new JSONObject( );
            JSONObject jsonData = new JSONObject( );
            jsonData.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
            jsonData.accumulate( SignalementRestConstants.JSON_TAG_USER, userJson );
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonData );
            return jsonAnswer.toString( );
        }
        catch( JSONException e )
        {
            AppLogService.error( e.getMessage( ), e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_JSON_REQUEST );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJson.format( error );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String checkMailAgent( JSONObject json ) {
        IFormatter<ErrorSignalement> formatterJson = new ErrorSignalementFormatterJson( );
        boolean res = false;
        try
        {
            String email = json.getString( SignalementRestConstants.JSON_TAG_EMAIL );
            if ( StringUtils.isBlank( email ) )
            {
                ErrorSignalement error = new ErrorSignalement( );
                error.setErrorCode( SignalementRestConstants.ERROR_BAD_USER_EMAIL );
                error.setErrorMessage( StringUtils.EMPTY );
                return formatterJson.format( error );
            }

            String[] listDomain = getEmailDomainAccept( );

            for(String domain : listDomain) {
                if( email.contains( domain ) )
                {
                    res = true;
                }
            }

            JSONObject jsonAnswer = new JSONObject( );
            JSONObject jsonData = new JSONObject( );
            jsonData.accumulate( SignalementRestConstants.JSON_TAG_STATUS, 0 );
            jsonData.accumulate( SignalementRestConstants.JSON_TAG_IS_AGENT, res );
            jsonAnswer.accumulate( SignalementRestConstants.JSON_TAG_ANSWER, jsonData );
            return jsonAnswer.toString( );
        }
        catch( JSONException e )
        {
            AppLogService.error( e.getMessage( ), e );
            ErrorSignalement error = new ErrorSignalement( );
            error.setErrorCode( SignalementRestConstants.ERROR_BAD_JSON_REQUEST );
            error.setErrorMessage( e.getMessage( ) );
            return formatterJson.format( error );
        }
    }
}

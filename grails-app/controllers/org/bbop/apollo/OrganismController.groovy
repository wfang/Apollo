package org.bbop.apollo

import org.bbop.apollo.gwt.shared.PermissionEnum
import grails.converters.JSON
import org.bbop.apollo.report.OrganismSummary
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject
import org.restapidoc.annotation.RestApi
import org.restapidoc.annotation.RestApiBodyObject
import org.restapidoc.annotation.RestApiMethod
import org.restapidoc.annotation.RestApiParam
import org.restapidoc.annotation.RestApiParams
import org.restapidoc.annotation.RestApiResponseObject
import org.restapidoc.pojo.RestApiParamType
import org.restapidoc.pojo.RestApiVerb
import org.springframework.http.HttpStatus

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@RestApi(name = "Organism Services", description = "Methods for managing users")
@Transactional(readOnly = true)
class OrganismController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def sequenceService
    def permissionService
    def requestHandlingService
    def preferenceService
    def organismService
    def reportService

    def chooseOrganismForJbrowse() {
        [organisms: Organism.findAllByPublicMode(true, [sort: 'commonName', order: 'asc']), flash: [message: params.error]]
    }

    @RestApiMethod(description = "Remove an organism", path = "/organism/deleteOrganism", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "organism", type = "json", paramType = RestApiParamType.QUERY, description = "Pass an Organism JSON object with an 'id' that corresponds to the id to delete")
    ])
    @Transactional
    def deleteOrganism() {
        try {
            JSONObject organismJson = (request.JSON ?: JSON.parse(params.data.toString())) as JSONObject
            log.debug "deleteOrganism ${organismJson}"
            if (permissionService.isUserAdmin(permissionService.getCurrentUser(organismJson))) {

                log.debug "organism ID: ${organismJson.id}"
                Organism organism = Organism.findById(organismJson.id as Long)
                if (!organism) {
                    organism = Organism.findByCommonName(organismJson.organism)
                }
                if (organism) {
                    UserOrganismPreference.deleteAll(UserOrganismPreference.findAllByOrganism(organism))
                    organism.delete()
                }
                log.info "Success deleting organism: ${organismJson.id}"
                render findAllOrganisms()
            } else {
                def error = [error: 'not authorized to delete organism']
                log.error(error.error)
                render error as JSON
            }
        }
        catch (Exception e) {
            def error = [error: 'problem deleting organism: ' + e]
            log.error(error.error)
            render error as JSON
        }
    }

    @RestApiMethod(description = "Remove features from an organism", path = "/organism/deleteOrganismFeatures", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "organism", type = "json", paramType = RestApiParamType.QUERY, description = "An organism json object that has an 'id' or 'commonName' parameter that corresponds to an organism.")
    ])
    @Transactional
    def deleteOrganismFeatures() {
        JSONObject organismJson = request.JSON ?: JSON.parse(params.data.toString()) as JSONObject
        if (organismJson.username == "" || organismJson.organism == "" || organismJson.password == "") {
            def error = ['error': 'Empty fields in request JSON']
            render error as JSON
            log.error(error.error)
            return
        }
        try {
            if (!permissionService.hasPermissions(organismJson, PermissionEnum.ADMINISTRATE)) {
                def error = [error: 'not authorized to delete all features from organism']
                log.error(error.error)
                render error as JSON
                return
            }

            Organism organism = Organism.findByCommonName(organismJson.organism)

            if (!organism) {
                organism = Organism.findById(organismJson.organism)
            }

            if (!organism) {
                throw new Exception("Can not find organism for ${organismJson.organism} to remove features of")
            }

            organismService.deleteAllFeaturesForOrganism(organism)
            render [:] as JSON
        }
        catch (e) {
            def error = [error: 'problem removing organism features for organism: ' + e]
            render error as JSON
            e.printStackTrace()
            log.error(error.error)
        }
    }

    @RestApiMethod(description = "Adds an organism returning a JSON array of all organisms", path = "/organism/addOrganism", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "organism", type = "json", paramType = RestApiParamType.QUERY, description = "An organism json object with the properties 'commonName' (required),'directory' (required), 'blatdb', 'species','genus'")
    ])
    @Transactional
    def addOrganism() {
        JSONObject organismJson = request.JSON ?: JSON.parse(params.data) as JSONObject
        try {
            if (permissionService.isUserAdmin(permissionService.getCurrentUser(organismJson))) {
                if (organismJson.get("commonName") == "" || organismJson.get("directory") == "") {
                    throw new Exception('empty fields detected')
                }

                log.debug "Adding ${organismJson.publicMode}"
                Organism organism = new Organism(
                        commonName: organismJson.commonName
                        , directory: organismJson.directory
                        , blatdb: organismJson.blatdb
                        , species: organismJson.species
                        , genus: organismJson.genus
                        , publicMode: organismJson.publicMode
                )
                log.debug "organism ${organism as JSON}"

                if (checkOrganism(organism)) {
                    organism.save(failOnError: true, flush: true, insert: true)
                }
                preferenceService.setCurrentOrganism(permissionService.getCurrentUser(organismJson), organism)
                sequenceService.loadRefSeqs(organism)
                render findAllOrganisms()
            } else {
                def error = [error: 'not authorized to add organism']
                render error as JSON
                log.error(error.error)
            }
        } catch (e) {
            def error = [error: 'problem saving organism: ' + e]
            render error as JSON
            e.printStackTrace()
            log.error(error.error)
        }
    }

    @RestApiMethod(description = "Finds sequences for a given organism and returns a JSON object including the username, organism and a JSONArray of sequences", path = "/organism/getSequencesForOrganism", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "organism", type = "string", paramType = RestApiParamType.QUERY, description = "Common name for the organism")
    ])
    def getSequencesForOrganism() {
        JSONObject organismJson = request.JSON ?: JSON.parse(params.data.toString()) as JSONObject
        if (organismJson.username == "" || organismJson.organism == "" || organismJson.password == "") {
            def error = ['error': 'Empty fields in request JSON']
            render error as JSON
            log.error(error.error)
            return
        }
        if (!permissionService.hasPermissions(organismJson, PermissionEnum.READ)) {
            render new JSONObject() as JSON
            return
        }
        String username = organismJson.username
        String organismCommonName = organismJson.organism
        JSONObject returnObject = new JSONObject()
        List<Sequence> sequenceList
        List<User> userList = User.executeQuery("select distinct u from User u where u.username = :username", [username: username])
        if (userList.size() == 0) {
            def error = ['error': 'Cannot find username ' + username + ' in the database']
            render error as JSON
            log.error(error.error)
            return
        }

        List<Organism> organismList = Organism.executeQuery("select distinct o from Organism o where o.commonName = :organismCommonName", [organismCommonName: organismCommonName])
        if (organismList.size() == 0) {
            def error = ['error': 'Cannot find organism ' + organismCommonName + ' in the database']
            render error as JSON
            log.error(error.error)
            return
        }

        if (permissionService.getOrganismPermissionsForUser(organismList[0], userList[0])[0].rank >= PermissionEnum.EXPORT.rank) {
            sequenceList = Sequence.executeQuery("select distinct s.name from Sequence s join s.featureLocations sf where s.organism.commonName = :organismCommonName", [organismCommonName: organismCommonName])
            println "Sequence list fetched at getSequencesForOrganism: ${sequenceList}"
        } else {
            def error = ['error': 'Username ' + username + ' does not have export permissions for organism ' + organismCommonName]
            render error as JSON
            log.error(error.error)
            return
        }

        returnObject.username = organismJson.username
        returnObject.organism = organismJson.organism
        returnObject.sequences = sequenceList as JSONArray
        render returnObject as JSON
    }

    private boolean checkOrganism(Organism organism) {
        File directory = new File(organism.directory)
        File trackListFile = new File(organism.getTrackList())
        File refSeqFile = new File(organism.getRefseqFile())

        if (!directory.exists() || !directory.isDirectory()) {
            organism.valid = false
            throw new Exception("Invalid directory specified: " + directory.absolutePath)
        } else if (!trackListFile.exists()) {
            organism.valid = false
            throw new Exception("Track file does not exists: " + trackListFile.absolutePath)
        } else if (!refSeqFile.exists()) {
            organism.valid = false
            throw new Exception("Reference sequence file does not exists: " + refSeqFile.absolutePath)
        } else {
            organism.valid = true
        }
        return organism.valid
    }


    @RestApiMethod(description = "Adds an organism returning a JSON array of all organisms", path = "/organism/updateOrganismInfo", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "organism", type = "json", paramType = RestApiParamType.QUERY, description = "An organism json object with the properties: 'id' (required), 'commonName' (required),'directory' (required), 'blatdb', 'species','genus'")
    ])
    @Transactional
    def updateOrganismInfo() {
        log.debug "updating organism info ${params}"
        try {
            JSONObject organismJson = request.JSON ?: JSON.parse(params.data.toString()) as JSONObject
            permissionService.checkPermissions(organismJson, PermissionEnum.ADMINISTRATE)
            Organism organism = Organism.findById(organismJson.id)
            if (organism) {
                log.debug "Adding public mode ${organismJson.publicMode}"
                organism.commonName = organismJson.name
                organism.blatdb = organismJson.blatdb
                organism.species = organismJson.species
                organism.genus = organismJson.genus
                organism.directory = organismJson.directory
                organism.publicMode = organismJson.publicMode

                if (checkOrganism(organism)) {
                    organism.save(flush: true, insert: false, failOnError: true)
                } else {
                    throw new Exception("Bad organism directory: " + organism.directory)
                }
            } else {
                throw new Exception('organism not found')
            }
            render findAllOrganisms()
        }
        catch (e) {
            def error = [error: 'problem saving organism: ' + e]
            render error as JSON
            log.error(error.error)
        }
    }

    @RestApiMethod(description = "Returns a JSON array of all organisms", path = "/organism/findAllOrganisms", verb = RestApiVerb.POST)
    @RestApiParams(params = [
            @RestApiParam(name = "username", type = "email", paramType = RestApiParamType.QUERY)
            , @RestApiParam(name = "password", type = "password", paramType = RestApiParamType.QUERY)
    ])
    def findAllOrganisms() {
        try {
            JSONObject organismJson = request.JSON ?: JSON.parse(params.data.toString()) as JSONObject
            List<Organism> putativeOrganismList = permissionService.getOrganismsForCurrentUser()
            List<Organism> organismList = []

            putativeOrganismList.each {
                List<PermissionEnum> permissionEnumList = permissionService.getOrganismPermissionsForUser(it,permissionService.currentUser)
                if(permissionEnumList.contains(PermissionEnum.ADMINISTRATE)){
                    organismList.add(it)
                }
            }

            if(!organismList){
                def error = [error: 'Must be admin to see organisms']
                render error as JSON
                return
            }

            UserOrganismPreference userOrganismPreference = UserOrganismPreference.findByUserAndCurrentOrganism(permissionService.currentUser, true)
            Long defaultOrganismId = userOrganismPreference ? userOrganismPreference.organism.id : null

            JSONArray jsonArray = new JSONArray()
            for (Organism organism in organismList) {
                Integer annotationCount = Feature.executeQuery("select count(distinct f) from Feature f left join f.parentFeatureRelationships pfr  join f.featureLocations fl join fl.sequence s join s.organism o  where f.childFeatureRelationships is empty and o = :organism and f.class in (:viewableTypes)", [organism: organism, viewableTypes: requestHandlingService.viewableAnnotationList])[0] as Integer
                Integer sequenceCount = Sequence.countByOrganism(organism)

                log.debug "findAllOrganismsfindAllOrganisms ${organism.publicMode}"
                JSONObject jsonObject = [
                        id             : organism.id,
                        commonName     : organism.commonName,
                        blatdb         : organism.blatdb,
                        directory      : organism.directory,
                        annotationCount: annotationCount,
                        sequences      : sequenceCount,
                        genus          : organism.genus,
                        species        : organism.species,
                        valid          : organism.valid,
                        publicMode     : organism.publicMode,
                        currentOrganism: defaultOrganismId != null ? organism.id == defaultOrganismId : false
                ] as JSONObject
                jsonArray.add(jsonObject)
            }
            render jsonArray as JSON
        }
        catch (Exception e) {
            def error = [error: e.message]
            render error as JSON
        }
    }

    /**
     * TODO: perOrganism summary
     * @param featureInstance
     * @return
     */
    def report() {
        if (!permissionService.checkPermissions(PermissionEnum.ADMINISTRATE)) {
            redirect(uri: "/auth/unauthorized")
            return
        }
        Map<Organism, OrganismSummary> organismSummaryListInstance = new TreeMap<>(new Comparator<Organism>() {
            @Override
            int compare(Organism o1, Organism o2) {
                return o1.commonName <=> o2.commonName
            }
        })

        // global version
        OrganismSummary organismSummaryInstance = reportService.generateAllFeatureSummary()


        Organism.listOrderByCommonName().each { organism ->
            OrganismSummary thisOrganismSummaryInstance = reportService.generateOrganismSummary(organism)
            organismSummaryListInstance.put(organism, thisOrganismSummaryInstance)
        }


        respond organismSummaryInstance, model: [organismSummaries: organismSummaryListInstance]
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'organism.label', default: 'Organism'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }

}

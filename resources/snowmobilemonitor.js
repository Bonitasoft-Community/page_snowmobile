'use strict';
/**
 * 
 */

(function() {


var appCommand = angular.module('snowmobilemonitor', ['googlechart', 'ui.bootstrap','ngSanitize', 'angularFileUpload']);

// Constant used to specify resource base path (facilitates integration into a Bonita custom page)
appCommand.constant('RESOURCE_PATH', 'pageResource?page=custompage_snowmobilemonitor&location=');


 
	   
// --------------------------------------------------------------------------
//
// Controler
//
// --------------------------------------------------------------------------
	   
// User app list controller
appCommand.controller('SnowMobileController', 
	function ( $http, $scope, $upload, $sce) {
		$('#calculupdatewait').hide();
		
		this.bdmfile ="";
	  	this.errormessage="";
		this.inprogress=false;
		this.includeDropTable=true;
		this.includeDropContent=true;
		this.policyChangeColumnType='ALTER_COLUMN';
		
		this.showSqlScript=true;
		this.showDelta=true;
		this.showDetail=true;
		
		this.info = function(  )
		{
			var self=this;

			self.inprogress=true;
			
			var t = new Date();
			
			var url='?page=custompage_snowmobile&action=info&t='+t;
			$http.get( url )
					.success( function ( jsonResult, statusHttp, headers, config ) {
						
						// connection is lost ?
						if (statusHttp==401 || typeof jsonResult === 'string') {
							console.log("Redirected to the login page !");
							window.location.reload();
						}	
						self.DatabaseMajorVersion					= jsonResult.DatabaseMajorVersion;
						self.DatabaseMinorVersion					= jsonResult.DatabaseMinorVersion;
						self.DatabaseProductName					= jsonResult.DatabaseProductName;
						self.DatabaseProductVersion					= jsonResult.DatabaseProductVersion;
						self.errormessage					= jsonResult.errormessage;
						self.inprogress						= false;
						}
					)
					.error( function ( result ) {
								self.errormessage					= jsonResult.errormessage;
								self.inprogress						= false;
								alert('error during get infocalcul update');
								}
								
					);		
		};
		this.info();

		
		this.calculupdate = function(  )
		{
			var self=this;
			self.inprogress						= true;
			var t = new Date();
			
			var url='?page=custompage_snowmobile&action=calculupdate&bdmfile='+this.bdmfile;
			url += "&includeDropTable="+this.includeDropTable
			url += "&includeDropContent="+this.includeDropContent
			url += "&policyChangeColumnType="+this.policyChangeColumnType
			url += "&t="+t;
		
				
			$http.get( url )
				.success( function ( jsonResult, statusHttp, headers, config ) {
					
					// connection is lost ?
					if (statusHttp==401 || typeof jsonResult === 'string') {
						console.log("Redirected to the login page !");
						window.location.reload();
					}	
					console.log("history",jsonResult);
					self.sqlupdate 					= jsonResult.sqlupdate;
					self.errormessage 				= jsonResult.errormessage; 	
					self.deltamessage				= jsonResult.deltamessage;
					self.message					= jsonResult.message
					self.inprogress						= false;
						})
				.error( function ( result ) {
						self.inprogress						= false;
						alert('error during calcul update');
							});		
		  // retrieve the list of process
		};
		
		// -----------------------------------------------------------------------------------------
		// tool
		// -----------------------------------------------------------------------------------------

		this.getHtml = function(listevents, sourceContext) {
			// console.log("getHtml:Start (r/o) source="+sourceContext);
			return $sce.trustAsHtml(listevents);
		}

		
		var me = this;
		$scope.$watch('files', function() {
			
			for (var i = 0; i < $scope.files.length; i++) {
				var file = $scope.files[i];
				// V6 : url is fileUpload
				// V7 : /bonita/portal/fileUpload
				$scope.upload = $upload.upload({
					url: '/bonita/portal/fileUpload',
					method: 'POST',
					data: {myObj: $scope.myModelObj},
					file: file
				}).progress(function(evt) {
//					console.log('progress: ' + parseInt(100.0 * evt.loaded / evt.total) + '% file :'+ evt.config.file.name);
				}).success(function(data, status, headers, config) {
					console.log('file ' + config.file.name + 'is uploaded successfully. Response: ' + data);
					me.bdmfile = data;
					me.calculupdate();
				});
			}
		});  
});
	
})();
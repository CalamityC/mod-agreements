package org.olf

import grails.gorm.multitenancy.Tenants
import org.olf.kb.RemoteKB
import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import org.olf.kb.Package;
import org.olf.kb.Platform;
import org.olf.kb.TitleInstance;
import org.olf.kb.PlatformTitleInstance;
import org.olf.kb.PackageContentItem;

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Transactional
public class PackageIngestService {

  def titleInstanceResolverService

  /**
   * Load the paackage data (Given in the agreed canonical json package format) into the KB.
   * This function must be passed VALID package data. At this point, all package contents are
   * assumed to be valid. Any invalid rows should be filtered out at this point.
   * @return id of package upserted
   */
  public Map upsertPackage(String tenantId, Map package_data) {
    
    def result = null;
    log.debug("PackageIngestService::upsertPackage(${tenantId},...)");

    Tenants.withId(tenantId) {
      result = internalUpsertPackage(package_data);
    }

    return result;
  }

  /**
   * Load the paackage data (Given in the agreed canonical json package format) into the KB.
   * This function must be passed VALID package data. At this point, all package contents are
   * assumed to be valid. Any invalid rows should be filtered out at this point.
   * This is to keep the implementation of this function clean, all error checking shoud be
   * performed prior to this step. This function is soley concerned with absorbing a valid
   * package into the KB.
   * @return id of package upserted
   */
  public Map internalUpsertPackage(Map package_data) {

    def result = [:];

    log.debug("Package header: ${package_data.header}");

    // header.packageSlug contains the package maintainers authoritative identifier for this package.
    def pkg = Package.findBySourceAndReference(package_data.header.packageSource, package_data.header.packageSlug)

    if ( pkg == null ) {
      pkg = new Package(
                             name: package_data.header.packageName,
                           source: package_data.header.packageSource,
                        reference: package_data.header.packageSlug).save(flush:true, failOnError:true);
    }

    int rownum = 0;
    package_data.packageContents.each { pc ->
      log.debug("Try to resolve ${pc}");
      if ( pc.instanceIdentifiers?.size() > 0 ) {
        TitleInstance title = titleInstanceResolverService.resolve(pc);

        if ( pc.platformUrl ) {
          log.debug("platform ${pc.platformUrl}");
          // lets try and work out the platform for the item
          try {
            Platform platform = Platform.resolve(pc.platformUrl);
            log.debug("Platform: ${platform}");

            // See if we already have a title platform record for the presence of this title on this platform
            PlatformTitleInstance pti = PlatformTitleInstance.findByTitleInstanceAndPlatform(title, platform)
            if ( pti == null ) 
              pti = new PlatformTitleInstance(titleInstance:title, platform:platform).save(flush:true, failOnError:true);

            // Lookup or create a package content item record for this title on this platform in this package
            PackageContentItem pci = PackageContentItem.findByPtiAndPackage(pti, pkg)
            if ( pci == null ) {
              pci = new PackageContentItem(pti:pti, pkg:pkg).save(flush:true, failOnError:true);
            }

            // If the row has a coverage statement, check that the range of coverage we know about for this title on this platform
            // extends to include the supplied information. It is a contract with the KB that we assume this is correct info.
            // We store this generally for the title on the platform, and specifically for this title in this package on this platform.
            if ( pc.coverage ) {

              // We define coverage to be a list in the exchange format, but sometimes it comes just as a JSON map. Convert that
              // to the list of mpas that coverageExtenderService.extend expects
              List cov = pc.coverage instanceof List ? pc.coverage : [ pc.coverage ]

              coverageExtenderService.extend(pti, cov, 'pti');
              coverageExtenderService.extend(pci, cov, 'pci');
              coverageExtenderService.extend(title, cov, 'ti');
            }
          }
          catch ( Exception e ) {
            log.error("problem",e);
          }
        }
        else {
          log.error("row ${rownum} No platform URL");
        }

        println("rownum ${rownum} Resolved title: ${pc.title} as ${title}");
      }
      else {
        log.error("row ${rownum} Skipping ${pc} - No identifiers.. This will change in an upcoming commit where we do normalised title matching");
      }

      // {
      //   "title": "Nordic Psychology",
      //   "instanceMedium": "electronic",
      //   "instanceMedia": "journal",
      //   "instanceIdentifiers": {
      //   "namespace": "jusp",
      //   "value": "12342"
      //   },
      //   "siblingInstanceIdentifiers": {
      //   "namespace": "issn",
      //   "value": "1901-2276"
      //   },
      //   "coverage": {
      //   "startVolume": "58",
      //   "startIssue": "1",
      //   "startDate": "2006-03-31T23:00:00Z",
      //   "endVolume": "63",
      //   "endIssue": "4",
      //   "endDate": "2011-12-31T00:00:00Z"
      //   },
      //   "embargo": null,
      //   "coverageDepth": "fulltext",
      //   "coverageNote": null
      //   }
    }

    return result;
  }
}

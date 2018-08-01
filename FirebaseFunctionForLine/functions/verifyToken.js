'use strict';
const admin = require('firebase-admin');
const rp = require('request-promise');

exports.handler = ((req, res) => {
  // Check Body
  req.checkBody('accessToken').notEmpty();

  const lineAccessToken = req.body.accessToken;

  req.getValidationResult()
    .then(result => {
      console.info('validating requested data.');
      if (!result.isEmpty()) {
        return res.status(400).json({ error: result.array() });
      }
      return null;
    }).then((errorRes) => {
      if (errorRes) {
        return null;
      } else {
        console.info('validated. (result OK)');
        return verifyLineToken(lineAccessToken)
          .then((customAuthToken) => {
            return res.status(200).json({ firebase_token: customAuthToken });
          }).catch(error => {
            return res.status(500).json({ error: error });
          });
      }
    }).catch(error => {
      console.error('Error: ', JSON.stringify(error));
    });
});

/**
 * Verify LINE access token and return a custom auth token allowing signing-in 
 * the corresponding Firebase account.
 *
 * Here are the steps involved:
 *  1. Get LINE's user profile from LINE server with LINE access token
 *  2. Check if a Firebase user corresponding to the LINE user already existed.
 *     If not, create user from LINE's user profile and custom user claim.
 *  3. Create user from LINE's user profile.
 *  4. Create Firebase Custom User Claim.
 *  5. Return a custom auth token allowing signing-in the Firebase account.
 *
 * @returns {Promise<string>} The Firebase custom auth token in a promise.
 */
function verifyLineToken(lineAccessToken) {
  // STEP 1: Get LINE's user profile from LINE server with LINE access token
  return rp({
    method: 'GET',
    url: 'https://api.line.me/v2/profile',
    json: true,
    timeout: 10 * 1000,
    headers: {
      'Authorization': `Bearer ${lineAccessToken}`,
    },
  }).then((response) => {
    if (!response.userId) {
      return Promise.reject(new Error('No userId.'));
    }
    console.log('Response: ' + JSON.stringify(response));

    const firebaseUid = 'line:' + response.userId;

    // STEP 2: Access token validation succeeded, so look up the corresponding Firebase user
    return admin.auth().getUser(firebaseUid)
      .then((userRecord) => {
        console.log(`user ${firebaseUid} was found.`);
        // If user exist, return this user record
        return Promise.resolve(userRecord);
      }).catch((error) => {
        console.log(`error.code: ${error.code}`);
        // If user does not exist, fetch LINE profile and create a Firebase new user with it
        if (error.code === 'auth/user-not-found') {
          // STEP 3: Create User from LINE's user profile
          const properties = {
            uid: firebaseUid,
          };
          if (response.displayName) properties.displayName = response.displayName;
          if (response.pictureUrl) properties.photoURL = response.pictureUrl;
          return admin.auth().createUser(properties).then((userRecord) => {
            console.log('created user successfully.');
            return Promise.resolve(userRecord);
          });

        } else {
          return Promise.reject(error);
        }
      });

  }).then((userRecord) => {
    console.log('UserRecord: ' + JSON.stringify(userRecord));
    // STEP 4: Create Firebase Custom User Claim
    return admin.auth().setCustomUserClaims(userRecord.uid, { provider: 'LINE' })
      .then(() => {
        // STEP 5: Generate Firebase Custom Auth Token
        return admin.auth().createCustomToken(userRecord.uid)
          .then((customAuthToken) => {
            return Promise.resolve(customAuthToken);
          })
      }).catch(error => {
        return Promise.reject(error);
      });
  });
}
